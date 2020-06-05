package tests;

import com.vividsolutions.jts.geom.Geometry;
import map.Application;
import map.MapController;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.FileUtil;
import org.apache.hadoop.fs.Path;
import org.apache.spark.api.java.JavaRDD;
import org.datasyslab.geospark.spatialRDD.SpatialRDD;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@Controller
public class TestController {
    private MapController map = new MapController();
    private static final String allRoutesLocation = "/dynamicmap/allRoutes";
    private static final String swapRoutesLocation = "/dynamicmap/allRoutes_swap";

    @RequestMapping(value = "/testOneByOne")
    public void testOneByOne(@RequestParam String stringAmbulancePoints) {
        String[] points = stringAmbulancePoints.split(",");
        for(String point : points)
            map.grid(point);
    }

    @RequestMapping(value = "/testAll")
    public void testAll(@RequestParam String stringAmbulancePoints, @RequestParam int howManyTimes) {
        for(int i = 0; i < howManyTimes; i++)
            map.grid(stringAmbulancePoints);
    }

    @RequestMapping(value = "/testIncrease")
    public void testIncrease(@RequestParam String stringAmbulancePoints) {
        String[] points = stringAmbulancePoints.split(",");
        for(int i = 0; i < points.length; i++) {
            StringBuilder requestParams = new StringBuilder("GEOMETRYCOLLECTION(");
            for(int j = 0; j < i; j++){
                requestParams.append(points[i]);
                requestParams.append(",");
            }
            requestParams.append(")");
            map.grid(requestParams.toString());
        }
    }

    @RequestMapping(value = "/testUpdate")
    public void testUpdate(@RequestParam String roadToUpdate, @RequestParam int howManyTimes) {
        for(int i = 0; i < howManyTimes; i++)
            map.update(roadToUpdate);
    }

    @RequestMapping(value = "/testDownloadSwap")
    public void testDownloadAndSwap(@RequestParam String stringAmbulancePoints) throws IOException {
        String[] points = stringAmbulancePoints.split(",");
        for(String point : points) {
            FileSystem hdfs = Application.hdfs;
            Path allRoutesPath = new Path(allRoutesLocation);
            Path swapRoutesPath = new Path(swapRoutesLocation);
            FileUtil.copy(hdfs, allRoutesPath, hdfs, swapRoutesPath, false, hdfs.getConf());
            map.grid(point);
            hdfs.delete(allRoutesPath, true);
            hdfs.rename(swapRoutesPath, allRoutesPath);
        }
    }
}
