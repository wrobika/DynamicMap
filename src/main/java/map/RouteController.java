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
    private static final String allRoutesLocation = "/dynamicmap/allRoutes";
    private static final String swapRoutesLocation = "/dynamicmap/allRoutes_swap";

    static SpatialRDD<Geometry> getAllRoutesRDD() throws IOException
    {
        FileSystem hdfs = Application.hdfs;
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
        replacedRDD.saveAsTextFile(swapRoutesLocation);
        FileSystem hdfs = Application.hdfs;
        Path allRoutesPath = new Path(allRoutesLocation);
        Path swapRoutesPath = new Path(swapRoutesLocation);
        hdfs.delete(allRoutesPath, true);
        hdfs.rename(swapRoutesPath, allRoutesPath);
    }

    public static void addNewRoutes(JavaRDD<Geometry> newRoutesRDD) throws IOException
    {
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
