package map;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.LineString;
import org.apache.spark.api.java.JavaRDD;
import org.datasyslab.geospark.formatMapper.GeoJsonReader;
import org.datasyslab.geospark.spatialRDD.SpatialRDD;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RDDController
{
    public static JavaRDD<Geometry> getRoutesRDDFromFiles()
    {
        JavaRDD<Geometry> allRoutesRDD = Application.sc.emptyRDD();
        List<JavaRDD<Geometry>> routeRDDsList = new ArrayList<>();
        try (Stream<Path> routeFiles = Files.walk(Paths.get(""))) {
            List<String> fileNames = routeFiles.map(Path::toString)
                    .filter(f -> f.startsWith("routes-"))
                    .collect(Collectors.toList());
            for (String file : fileNames) {
                SpatialRDD<Geometry> routesFromOnePoint = GeoJsonReader.readToGeometryRDD(Application.sc, file);
                routeRDDsList.add(routesFromOnePoint.rawSpatialRDD);
            }
            allRoutesRDD = Application.sc.union(allRoutesRDD, routeRDDsList);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return allRoutesRDD;
    }

    public static void readRoutesFromRDD()
    {

    }
}
