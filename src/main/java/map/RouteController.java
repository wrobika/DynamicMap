package map;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.storage.StorageLevel;
import org.datasyslab.geospark.formatMapper.WktReader;
import org.datasyslab.geospark.spatialOperator.RangeQuery;
import org.datasyslab.geospark.spatialRDD.SpatialRDD;
import scala.App;

import java.io.IOException;
import java.util.List;

public class RouteController {

    private static final String allRoutesLocation = "/dynamicmap/allRoutes";
    private static final String swapRoutesLocation = "/dynamicmap/allRoutes_swap";

    static SpatialRDD<Geometry> getAllRoutesRDD() throws IOException {
        FileSystem hdfs = Application.hdfs;
        Path path = new Path(allRoutesLocation);
        if(!hdfs.exists(path)) {
            SpatialRDD<Geometry> emptyRDD = new SpatialRDD<>();
            emptyRDD.setRawSpatialRDD(Application.sc.emptyRDD());
            return emptyRDD;
        }
        SpatialRDD<Geometry> allRoutes = WktReader.readToGeometryRDD(Application.sc, allRoutesLocation, 0, true, false);
        allRoutes.rawSpatialRDD.persist(StorageLevel.MEMORY_ONLY());
        return allRoutes;
    }

    public static JavaRDD<Geometry> findIntersectedRoutes(LineString road) throws Exception {
        return RangeQuery.SpatialRangeQuery(
                Application.allRoutes, road.buffer(0.00002), true, false);
    }

    public static void replaceRoutes(JavaRDD<Geometry> elementsToReplaceRDD,
                                     JavaRDD<Geometry> elementsReplacingRDD) throws IOException {
        JavaRDD<Geometry> subtractedRDD = Application.allRoutes.rawSpatialRDD
                .subtract(elementsToReplaceRDD);
        JavaRDD<Geometry> replacedRDD = subtractedRDD.union(elementsReplacingRDD);
        replacedRDD.coalesce(16, true)
		    .saveAsTextFile(swapRoutesLocation);
        FileSystem hdfs = Application.hdfs;
        Path allRoutesPath = new Path(allRoutesLocation);
        Path swapRoutesPath = new Path(swapRoutesLocation);
        hdfs.delete(allRoutesPath, true);
        hdfs.rename(swapRoutesPath, allRoutesPath);
        Application.allRoutes = getAllRoutesRDD();
    }

    public static void addNewRoutes(JavaRDD<Geometry> newRoutesRDD) throws IOException {
        JavaRDD<Geometry> unionRDD = Application.allRoutes.rawSpatialRDD
                .union(newRoutesRDD);
        unionRDD.persist(StorageLevel.MEMORY_ONLY());
        Application.allRoutes.setRawSpatialRDD(unionRDD);
        //unionRDD.coalesce(16, true);
		    //.saveAsTextFile(allRoutesLocation);
        //Application.allRoutes = getAllRoutesRDD();
    }

    public static Point getStartPoint(Geometry route) {
        return ((LineString) route).getStartPoint();
    }

    public static Point getEndPoint(Geometry route) {
        return ((LineString) route).getEndPoint();
    }
}
