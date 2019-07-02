package map;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;
import org.apache.spark.api.java.JavaRDD;
import org.datasyslab.geospark.enums.FileDataSplitter;
import org.datasyslab.geospark.formatMapper.GeoJsonReader;
import org.datasyslab.geospark.formatMapper.shapefileParser.ShapefileReader;
import org.datasyslab.geospark.spatialRDD.LineStringRDD;
import org.datasyslab.geospark.spatialRDD.PointRDD;
import org.datasyslab.geospark.spatialRDD.PolygonRDD;
import org.datasyslab.geospark.spatialRDD.SpatialRDD;

import java.awt.geom.Point2D;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static java.lang.Math.cos;

public class PointGridController
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
                    .filter(point -> krakowPolygon.contains(point))
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

    public static List<TimePoint> getTimePointGrid()
    {
        boolean withTime = true;
        List<Point> pointList = getPointList(withTime);
        ArrayList<TimePoint> timePoints = new ArrayList<>();
        pointList.forEach(point -> timePoints.add(
                new TimePoint(Double.parseDouble(point.getUserData().toString()),
                        new Point2D.Double(point.getX(), point.getY()))
        ));
        return timePoints;
    }

    public static List<Point> getPointGrid()
    {
        boolean carryOtherAtributes = false;
        return getPointList(carryOtherAtributes);
    }

    private static List<Point> getPointList(boolean withTime)
    {
        String pointRDDInputLocation = "/home/weronika/magisterka/DynamicMap/grid.csv";
        int pointRDDOffset = 0;
        FileDataSplitter pointRDDSplitter = FileDataSplitter.CSV;
        PointRDD objectRDD = new PointRDD(Application.sc, pointRDDInputLocation, pointRDDOffset, pointRDDSplitter, withTime);

        Long pointsCount = objectRDD.rawSpatialRDD.count();
        List<Point> pointList = objectRDD.rawSpatialRDD.take(pointsCount.intValue());
        return pointList;
    }

    public static List<TimePoint> readPointFromJSON(Point startPoint)
    {
        //TODO: jedna metoda zwracająca nazwę pliku dla punktu
        String inputLocation = "routes-" + String.valueOf(startPoint.getX())
                +"-"+ String.valueOf(startPoint.getY()) +".json";
        SpatialRDD spatialRDD = GeoJsonReader.readToGeometryRDD(Application.sc, inputLocation);
        Long pointsCount = spatialRDD.rawSpatialRDD.count();
        List<LineString> routeList = spatialRDD.rawSpatialRDD.take(pointsCount.intValue());
        ArrayList<TimePoint> timePoints = new ArrayList<>();
        for(LineString route : routeList) {
            String[] userData = route.getUserData().toString().split("\t");
            String[] endCoordinates = userData[1]
                    .substring(1, userData[1].length() - 1)
                    .split(",");
            Double x = Double.valueOf(endCoordinates[1]);
            Double y = Double.valueOf(endCoordinates[0]);
            Double time = Double.valueOf(userData[2]);
            timePoints.add(new TimePoint(time, new Point2D.Double(x, y)));
        }
        return timePoints;
    }
}
