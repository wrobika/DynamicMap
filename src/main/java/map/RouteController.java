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

    public static Coordinate getStartCoord(Geometry route)
    {
        return ((LineString)route).getStartPoint().getCoordinate();
    }

    public static Coordinate getEndCoord(Geometry route)
    {
        return ((LineString)route).getEndPoint().getCoordinate();
    }
}
