package map;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.spark.api.java.JavaRDD;
import org.datasyslab.geospark.formatMapper.WktReader;
import org.datasyslab.geospark.spatialOperator.RangeQuery;
import org.datasyslab.geospark.spatialRDD.SpatialRDD;

import java.io.IOException;
import java.util.List;

public class RouteController
{
    private static final String allRoutesLocation = "allRoutes";

    static SpatialRDD<Geometry> getAllRoutesRDD() throws IOException
    {
        FileSystem hdfs = FileSystem.get(Application.sc.hadoopConfiguration());
        Path path = new Path(allRoutesLocation);
        if(!hdfs.exists(path))
        {
            SpatialRDD<Geometry> emptyRDD = new SpatialRDD<>();
            emptyRDD.setRawSpatialRDD(Application.sc.emptyRDD());
            return emptyRDD;
        }
        return WktReader.readToGeometryRDD(Application.sc, allRoutesLocation, 0, true, false);
    }

    public static JavaRDD<Geometry> findIntersectedRoutes(LineString road) throws Exception
    {
        SpatialRDD<Geometry> allRoutesRDD = getAllRoutesRDD();
        return RangeQuery.SpatialRangeQuery(allRoutesRDD, road, true, false);
    }

    public static void replaceRoutes(JavaRDD<Geometry> elementsToReplaceRDD,
                                     JavaRDD<Geometry> elementsReplacingRDD) throws IOException
    {
        SpatialRDD<Geometry> allRoutesRDD = getAllRoutesRDD();
        JavaRDD<Geometry> subtractedRDD = allRoutesRDD.rawSpatialRDD
                .subtract(elementsToReplaceRDD);
        JavaRDD<Geometry> replacedRDD = subtractedRDD.union(elementsReplacingRDD);
        //TODO: jakiś backup
        replacedRDD.saveAsTextFile(allRoutesLocation);
    }

    public static void addNewRoutes(List<Geometry> newRoutes) throws IOException
    {
        JavaRDD<Geometry> newRoutesRDD = Application.sc.parallelize(newRoutes);
        SpatialRDD<Geometry> allRoutesRDD = getAllRoutesRDD();
        JavaRDD<Geometry> unionRDD = allRoutesRDD.rawSpatialRDD.union(newRoutesRDD);
        unionRDD.saveAsTextFile(allRoutesLocation);
    }

    public static Point getStartPoint(Geometry route)
    {
        if(route instanceof LineString)
        {
            return ((LineString) route).getStartPoint();
        }
        //TODO: bardzo brzydki null
        return null;
    }

    public static Point getEndPoint(Geometry route)
    {
        if(route instanceof LineString)
        {
            return ((LineString) route).getEndPoint();
        }
        //TODO: bardzo brzydki null
        return null;
    }
}
