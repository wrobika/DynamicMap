package map;

import com.vividsolutions.jts.geom.*;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
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
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.Math.cos;
import static map.RouteController.getAllRoutesRDD;
import static map.RouteController.getEndPoint;
import static map.RouteController.getStartPoint;

public class GridController
{

    private static final String irregularGridFile = "/dynamicmap/irregularGrid.csv";
    private static final String regularGridFile = "/dynamicmap/grid.csv";
    private static final String boundaryKrakowLocation = "/dynamicmap/granicaKrakowa";

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
        List<Point> points = new ArrayList<>();
        String pointRDDInputLocation = irregularGridFile;
        if(!fitToRoadNetwork)
        {
            pointRDDInputLocation = regularGridFile;
        }
        try
        {
            FileSystem hdfs = Application.hdfs;
            Path path = new Path(pointRDDInputLocation);
            if(!hdfs.exists(path))
            {
                Configuration conf = new Configuration();
                FileSystem localFS = FileSystem.getLocal(conf);
                  Path localPath = new Path(localFS.getHomeDirectory().toString() +
                        pointRDDInputLocation);
                hdfs.copyFromLocalFile(localPath, path);
            }
            int pointRDDOffset = 0;
            FileDataSplitter pointRDDSplitter = FileDataSplitter.CSV;
            PointRDD pointsRDD = new PointRDD(Application.sc, pointRDDInputLocation, pointRDDOffset, pointRDDSplitter, false);
            points = pointsRDD.rawSpatialRDD.collect();
        }
        catch(IOException ex)
        {
            ex.printStackTrace();
        }
        return points;
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

    static Map<Point, Double> getTimeGrid() throws Exception
    {
        List<Point> ambulances = Application.ambulances;
        if(ambulances.isEmpty())
        {
            return getEmptyGrid(true);
        }
        SpatialRDD<Geometry> allRoutesRDD = getAllRoutesRDD();

        JavaRDD<Geometry> routesFromAmbulancesLocationRDD = allRoutesRDD.rawSpatialRDD
                .filter(route -> ambulances.contains(getStartPoint(route)));

        long foundAmbulanceCoordCount = routesFromAmbulancesLocationRDD
                .map(RouteController::getStartPoint)
                .distinct().count();
        if(foundAmbulanceCoordCount < ambulances.size())
        {
            routesFromAmbulancesLocationRDD = downloadMissingData(ambulances, routesFromAmbulancesLocationRDD);
        }

        JavaPairRDD<Point, Double> timeGrid = routesFromAmbulancesLocationRDD
                .mapToPair(GridController::getPointTime)
                .reduceByKey((time1,time2) -> (time2 < time1) ? time2 : time1);
        return timeGrid.collectAsMap();
    }

    private static JavaRDD<Geometry> downloadMissingData(List<Point> ambulances,
                                                         JavaRDD<Geometry> routesFromAmbulancesLocationRDD) throws Exception
    {
        List<Point> foundAmbulances = routesFromAmbulancesLocationRDD
                .map(RouteController::getStartPoint)
                .distinct()
                .collect();

        List<Point> pointsToDownload = new ArrayList<>(ambulances);
        pointsToDownload.removeAll(foundAmbulances);
        DownloadController.downloadRoutes(pointsToDownload);

        SpatialRDD<Geometry> allRoutesRDD = getAllRoutesRDD();
        //lub nie filtrowac allRoutes na nowo, tylko dolaczyc pobrane do routesFromAmbulancePointsRDD
        routesFromAmbulancesLocationRDD = allRoutesRDD.rawSpatialRDD
                .filter(route -> ambulances.contains(getStartPoint(route)));
        return routesFromAmbulancesLocationRDD;
    }

    private static Tuple2<Point, Double> getPointTime(Geometry route)
    {
        Point point = getEndPoint(route);
        Double time = Double.valueOf(route.getUserData().toString());
        return new Tuple2<>(point, time);
    }
}
