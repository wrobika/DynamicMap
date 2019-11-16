package map;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.LineString;
import org.apache.commons.io.FileUtils;
import org.apache.spark.api.java.JavaRDD;
import org.datasyslab.geospark.formatMapper.WktReader;
import org.datasyslab.geospark.spatialOperator.RangeQuery;
import org.datasyslab.geospark.spatialRDD.SpatialRDD;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class RouteController
{
    public static String routesOSRMLocation = "allRoutesOSRM.csv";
    private static String routesGeoSparkLocation = "allRoutes";

    static SpatialRDD<Geometry> getAllRoutesRDD()
    {
        Path path = Paths.get(routesGeoSparkLocation);
        if(Files.notExists(path))
        {
            path = Paths.get(routesOSRMLocation);
            if(Files.notExists(path))
            {
                SpatialRDD<Geometry> emptyRDD = new SpatialRDD<>();
                emptyRDD.setRawSpatialRDD(Application.sc.emptyRDD());
                return emptyRDD;
            }
        }
        return WktReader.readToGeometryRDD(Application.sc, routesGeoSparkLocation, 0, true, false);
    }

    public static JavaRDD<Geometry> findIntersectedRoutes(LineString road)
    {
        JavaRDD<Geometry> intersectedRoutesRDD = Application.sc.emptyRDD();
        try
        {
            SpatialRDD<Geometry> allRoutesRDD = getAllRoutesRDD();
            intersectedRoutesRDD = RangeQuery.SpatialRangeQuery(allRoutesRDD, road, true, false);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return intersectedRoutesRDD;
    }

    public static void replaceRoutes(JavaRDD<Geometry> elementsToReplaceRDD,
                                     JavaRDD<Geometry> elementsReplacingRDD)
    {
        try {
            SpatialRDD<Geometry> allRoutesRDD = getAllRoutesRDD();
            JavaRDD<Geometry> subtractedRDD = allRoutesRDD.rawSpatialRDD
                    .subtract(elementsToReplaceRDD);
            JavaRDD<Geometry> replacedRDD = subtractedRDD.union(elementsReplacingRDD);
            allRoutesRDD.setRawSpatialRDD(replacedRDD);
            //TODO: jakiś backup
            FileUtils.deleteDirectory(new File(routesGeoSparkLocation));
            allRoutesRDD.rawSpatialRDD.saveAsTextFile(routesGeoSparkLocation);
        }
        catch(IOException ex)
        {
            ex.printStackTrace();
        }
    }

    public static Coordinate getStartCoord(Geometry route)
    {
        return route.getCoordinates()[0];
    }

    public static Coordinate getEndCoord(Geometry route)
    {
        Coordinate[] coordinates = route.getCoordinates();
        int length = coordinates.length;
        return coordinates[length-1];
    }
}
