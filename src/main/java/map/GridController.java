package map;

import com.vividsolutions.jts.geom.*;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.storage.StorageLevel;
import org.datasyslab.geospark.enums.FileDataSplitter;
import org.datasyslab.geospark.formatMapper.GeoJsonReader;
import org.datasyslab.geospark.formatMapper.shapefileParser.ShapefileReader;
import org.datasyslab.geospark.spatialRDD.PointRDD;
import org.datasyslab.geospark.spatialRDD.PolygonRDD;
import org.datasyslab.geospark.spatialRDD.SpatialRDD;
import osrm.OsrmController;
import scala.Tuple2;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.Math.cos;
import static map.RouteController.getAllRoutesRDD;

public class GridController
{
    public static void createGrid()
    {
        //TODO:zrobic pisanie przez geosparka
        //TODO:spytac jak obliczyc 250m?

        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter("grid.csv"));
            GeometryFactory geometryFactory = new GeometryFactory();
            List<Point> pointList = new ArrayList<>();

            //https://stackoverflow.com/questions/639695/how-to-convert-latitude-or-longitude-to-meters
            for (Double lat = 49.964071; lat < 50.133260; lat += 0.00224578) {
                Double lat_radian = lat * 3.14 / 180;
                for (Double lon = 19.786568; lon < 20.228223; lon += 0.00224579 / cos(lat_radian)) {
                    Point point = geometryFactory.createPoint(new Coordinate(lon, lat));
                    pointList.add(point);
                }
            }

            String shapefileInputLocation = "/home/weronika/magisterka/granicaKrakowa";
            SpatialRDD krakowRDD = ShapefileReader.readToGeometryRDD(Application.sc, shapefileInputLocation);
            PolygonRDD krakowPolygonRDD = ShapefileReader.geometryToPolygon(krakowRDD);
            Polygon krakowPolygon = krakowPolygonRDD.rawSpatialRDD.first();

            List<Point> pointInKrakowList = pointList.stream()
                    .filter(krakowPolygon::contains)
                    .collect(Collectors.toList());
            for(Point point : pointInKrakowList)
            {
                String pointString = String.valueOf(point.getX()) + "," + String.valueOf(point.getY()) + "\n";
                writer.write(pointString);
            }
            writer.close();
        }
        catch(Exception ex)
        {
            System.out.println(ex.getMessage());
        }
    }

    public static List<Point> getGrid(boolean fitToRoadNetwork)
    {
        String pointRDDInputLocation = "/home/weronika/magisterka/DynamicMap/irregularGrid.csv";
        if(!fitToRoadNetwork)
        {
            pointRDDInputLocation = "/home/weronika/magisterka/DynamicMap/grid.csv";
        }
        int pointRDDOffset = 0;
        FileDataSplitter pointRDDSplitter = FileDataSplitter.CSV;
        PointRDD pointsRDD = new PointRDD(Application.sc, pointRDDInputLocation, pointRDDOffset, pointRDDSplitter, false);
        return pointsRDD.rawSpatialRDD.collect();
    }

    static Map<Point, Double> getEmptyGrid(boolean fitToRoadNetwork)
    {
        List<Point> points = getGrid(fitToRoadNetwork);
        Map<Point, Double> emptyGrid = new HashMap<>();
        for (Point point : points)
        {
            emptyGrid.put(point, 3000.0);
        }
        return emptyGrid;
    }

    static Map<Point, Double> getTimeGrid(List<Point> ambulancePoints)
    {
        if(ambulancePoints.isEmpty())
        {
            return getEmptyGrid(true);
        }
        SpatialRDD<Geometry> allRoutesRDD = getAllRoutesRDD();
        List<String> ambulanceCoordinates = mapToStringCoordinatesList(ambulancePoints);
        JavaRDD<Geometry> routesFromAmbulancePointsRDD = allRoutesRDD.rawSpatialRDD
                .filter(route -> ambulanceCoordinates.contains(getStartCoord(route)));
        long foundAmbulanceCoordCount = routesFromAmbulancePointsRDD
                .map(GridController::getStartCoord)
                .distinct().count();
        if(foundAmbulanceCoordCount < ambulanceCoordinates.size())
        {
            routesFromAmbulancePointsRDD = downloadMissingData(ambulanceCoordinates, routesFromAmbulancePointsRDD);
        }
        JavaPairRDD<Point, Double> timeGrid = routesFromAmbulancePointsRDD
                .mapToPair(GridController::getPointTime)
                .reduceByKey((time1,time2) -> (time2 < time1) ? time2 : time1);
        return timeGrid.collectAsMap();
    }

    private static JavaRDD<Geometry> downloadMissingData(List<String> ambulanceCoordinates, JavaRDD<Geometry> routesFromAmbulancePointsRDD)
    {
        List<String> foundAmbulanceCoordinates = routesFromAmbulancePointsRDD
                .map(GridController::getStartCoord)
                .distinct()
                .collect();
        List<String> coordinatesToDownload = new ArrayList<>(ambulanceCoordinates);
        coordinatesToDownload.removeAll(foundAmbulanceCoordinates);
        List<Point> pointsToDownload = mapToPointList(coordinatesToDownload);
        OsrmController.downloadRoutes(pointsToDownload);

        SpatialRDD<Geometry> allRoutesRDD = getAllRoutesRDD();
        //lub nie filtrowac na nowo, tylko dolaczyc pobrane
        routesFromAmbulancePointsRDD = allRoutesRDD.rawSpatialRDD
                .filter(route -> ambulanceCoordinates.contains(getStartCoord(route)));
        return routesFromAmbulancePointsRDD;
    }



    private static List<Point> mapToPointList(List<String> stringCoordinatesList)
    {
        return stringCoordinatesList.stream()
                .map(GridController::mapToPoint)
                .collect(Collectors.toList());
    }

    private static List<String> mapToStringCoordinatesList(List<Point> ambulancePoints)
    {
        return ambulancePoints.stream()
                .map(point ->
                    String.format(Locale.US, "[%.6f, %.6f]", point.getX(), point.getY()))
                .collect(Collectors.toList());
    }

    /*
    return String "[x, y]" example: "[15.888, 40.222]"
     */
    private static String getStartCoord(Geometry route)
    {
        String[] userData = route.getUserData()
                .toString()
                .split("\t");
        return userData[0];
    }

    private static Tuple2<Point, Double> getPointTime(Object route)
    {
        String[] userData = ((LineString)route).getUserData()
                .toString()
                .split("\t");
        Point point = mapToPoint(userData[1]);
        Double time = Double.valueOf(userData[2]);
        return new Tuple2<>(point, time);
    }

    private static Point mapToPoint(String stringCoordinates)
    {
        if(stringCoordinates.startsWith("["))
        {
            stringCoordinates = stringCoordinates.substring(1, stringCoordinates.length() - 1);
        }
        String[] coordinates = stringCoordinates.split(",");
        Double x = Double.valueOf(coordinates[0]);
        Double y = Double.valueOf(coordinates[1]);
        GeometryFactory geometryFactory = new GeometryFactory();
        return geometryFactory.createPoint(new Coordinate(x,y));
    }
}
