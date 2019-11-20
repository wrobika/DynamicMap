package map;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.LineString;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.spark.api.java.JavaRDD;
import org.datasyslab.geospark.formatMapper.WktReader;
import org.datasyslab.geospark.spatialOperator.RangeQuery;
import org.datasyslab.geospark.spatialRDD.SpatialRDD;

import java.io.IOException;

public class RouteController
{
    public static String routesOSRMLocation = "allRoutesOSRM";
    private static String routesGeoSparkLocation = "allRoutes";

    static SpatialRDD<Geometry> getAllRoutesRDD() throws IOException
    {
        FileSystem hdfs = FileSystem.get(Application.sc.hadoopConfiguration());
        Path path = new Path(routesGeoSparkLocation);
        if(!hdfs.exists(path))
        {
            path = new org.apache.hadoop.fs.Path(routesOSRMLocation);
            if(!hdfs.exists(path))
            {
                SpatialRDD<Geometry> emptyRDD = new SpatialRDD<>();
                emptyRDD.setRawSpatialRDD(Application.sc.emptyRDD());
                return emptyRDD;
            }
            return WktReader.readToGeometryRDD(Application.sc, routesOSRMLocation, 0, true, false);
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
            allRoutesRDD.rawSpatialRDD.saveAsTextFile(routesGeoSparkLocation);
        }
        catch(IOException ex)
        {
            ex.printStackTrace();
        }
    }

    public static Coordinate getStartCoord(Geometry route)
    {
        return ((LineString)route).getStartPoint().getCoordinate();
    }

    public static Coordinate getEndCoord(Geometry route)
    {
        return ((LineString)route).getEndPoint().getCoordinate();
    }
}
