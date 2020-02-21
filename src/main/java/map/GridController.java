package map;

import com.vividsolutions.jts.geom.*;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.datasyslab.geospark.enums.FileDataSplitter;
import org.datasyslab.geospark.formatMapper.shapefileParser.ShapefileReader;
import org.datasyslab.geospark.spatialRDD.PointRDD;
import org.datasyslab.geospark.spatialRDD.PolygonRDD;
import org.datasyslab.geospark.spatialRDD.SpatialRDD;
import osrm.DownloadController;
import scala.Tuple2;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.Math.cos;
import static map.RouteController.getAllRoutesRDD;
import static map.RouteController.getStartCoord;

public class GridController
{

    private static final String irregularGridFile = "irregularGrid.csv";
    private static final String regularGridFile = "grid.csv";
    private static final String boundaryKrakowLocation = "granicaKrakowa";

    public static void createGrid()
    {
        //TODO:zrobic pisanie przez geosparka
        //TODO:spytac jak obliczyc 250m?

        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(regularGridFile));
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

            SpatialRDD krakowRDD = ShapefileReader.readToGeometryRDD(Application.sc, boundaryKrakowLocation);
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
        String pointRDDInputLocation = irregularGridFile;
        if(!fitToRoadNetwork)
        {
            pointRDDInputLocation = regularGridFile;
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

    static Map<Point, Double> getTimeGrid(List<Coordinate> ambulanceCoordinates) throws Exception
    {
        if(ambulanceCoordinates.isEmpty())
        {
            return getEmptyGrid(true);
        }
        SpatialRDD<Geometry> allRoutesRDD = getAllRoutesRDD();

        JavaRDD<Geometry> routesFromAmbulancePointsRDD = allRoutesRDD.rawSpatialRDD
                .filter(route -> ambulanceCoordinates.contains(getStartCoord(route)));

        long foundAmbulanceCoordCount = routesFromAmbulancePointsRDD
                .map(RouteController::getStartCoord)
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

    private static JavaRDD<Geometry> downloadMissingData(List<Coordinate> ambulanceCoordinates,
                                                         JavaRDD<Geometry> routesFromAmbulancePointsRDD) throws Exception
    {
        List<Coordinate> foundAmbulanceCoordinates = routesFromAmbulancePointsRDD
                .map(RouteController::getStartCoord)
                .distinct()
                .collect();
        List<Coordinate> coordinatesToDownload = new ArrayList<>(ambulanceCoordinates);
        coordinatesToDownload.removeAll(foundAmbulanceCoordinates);
        DownloadController.downloadRoutes(coordinatesToDownload);

        SpatialRDD<Geometry> allRoutesRDD = getAllRoutesRDD();
        //lub nie filtrowac allRoutes na nowo, tylko dolaczyc pobrane do routesFromAmbulancePointsRDD
        routesFromAmbulancePointsRDD = allRoutesRDD.rawSpatialRDD
                .filter(route -> ambulanceCoordinates.contains(getStartCoord(route)));
        return routesFromAmbulancePointsRDD;
    }

    private static Tuple2<Point, Double> getPointTime(Geometry route)
    {
        Point point = ((LineString) route).getEndPoint();
        Double time = Double.valueOf(route.getUserData().toString());
        return new Tuple2<>(point, time);
    }
}
