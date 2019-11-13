package map;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.LineString;
import org.apache.spark.api.java.JavaRDD;
import org.datasyslab.geospark.formatMapper.GeoJsonReader;
import org.datasyslab.geospark.spatialOperator.RangeQuery;
import org.datasyslab.geospark.spatialRDD.SpatialRDD;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class RouteController
{
    public static String allRoutesLocation = "allRoutes.json";

    static SpatialRDD<Geometry> getAllRoutesRDD()
    {
        Path path = Paths.get(allRoutesLocation);
        if(Files.notExists(path))
        {
            SpatialRDD<Geometry> emptyRDD = new SpatialRDD<>();
            emptyRDD.setRawSpatialRDD(Application.sc.emptyRDD());
            return emptyRDD;
        }
        return GeoJsonReader.readToGeometryRDD(Application.sc, allRoutesLocation);
    }

    public static JavaRDD findIntersectedRoutes(LineString road)
    {
        JavaRDD intersectedRoutesRDD = Application.sc.emptyRDD();
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
}
