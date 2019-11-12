package map;

import com.vividsolutions.jts.geom.*;
import org.apache.commons.math3.util.Pair;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.function.Function;
import org.datasyslab.geospark.enums.FileDataSplitter;
import org.datasyslab.geospark.formatMapper.GeoJsonReader;
import org.datasyslab.geospark.formatMapper.shapefileParser.ShapefileReader;
import org.datasyslab.geospark.spatialRDD.LineStringRDD;
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

    public static String fileName(Point point)
    {
        return "routes-" + String.valueOf(point.getX())
                +"-"+ String.valueOf(point.getY()) +".json";
    }

    //NEW VERSION
    static Map<Point, Double> getTimeGrid(List<Point> ambulancePoints)
    {
        if(ambulancePoints.isEmpty())
        {
            return getEmptyGrid(true);
        }

        String inputLocation = "allRoutes.json";
        Path path = Paths.get(inputLocation);
        if(Files.notExists(path))
        {
            OsrmController.downloadRoutes(ambulancePoints, inputLocation);
        }
        SpatialRDD<Geometry> allRoutesRDD = GeoJsonReader.readToGeometryRDD(Application.sc, inputLocation);

        List<String> ambulanceCoordinates = getStringCoordinates(ambulancePoints);
        JavaRDD<Geometry> routesFromAmbulancePointsRDD = allRoutesRDD.rawSpatialRDD
                .filter(route -> ambulanceCoordinates.contains(getStartCoord(route)));

        JavaPairRDD<Point, Double> timeGrid = routesFromAmbulancePointsRDD
                .mapToPair(GridController::getPointTime)
                .reduceByKey((time1,time2) -> (time2 < time1) ? time2 : time1);

        return timeGrid.collectAsMap();
    }

    private static List<String> getStringCoordinates(List<Point> ambulancePoints)
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
        String[] endCoordinates = userData[1]
                .substring(1, userData[1].length() - 1)
                .split(",");
        Double x = Double.valueOf(endCoordinates[0]);
        Double y = Double.valueOf(endCoordinates[1]);
        Double time = Double.valueOf(userData[2]);
        GeometryFactory geometryFactory = new GeometryFactory();
        Point point = geometryFactory.createPoint(new Coordinate(x,y));
        return new Tuple2<>(point, time);
    }
}
