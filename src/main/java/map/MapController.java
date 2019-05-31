package map;

import com.vividsolutions.jts.geom.Point;
import org.datasyslab.geospark.enums.FileDataSplitter;
import org.datasyslab.geospark.spatialRDD.PointRDD;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

@Controller
public class MapController {

    @GetMapping("/map")
    public String map(Model model) {
        String pointRDDInputLocation = "/home/weronika/magisterka/DynamicMap/grid.csv";
        int pointRDDOffset = 0;
        FileDataSplitter pointRDDSplitter = FileDataSplitter.CSV;
        boolean carryOtherAttributes = true;
        PointRDD objectRDD = new PointRDD(Application.sc, pointRDDInputLocation, pointRDDOffset, pointRDDSplitter, carryOtherAttributes);

        Long pointsCount = objectRDD.rawSpatialRDD.count();
        List<Point> pointList = objectRDD.rawSpatialRDD.take(pointsCount.intValue());
        ArrayList<TimePoint> timePoints = new ArrayList<>();
        pointList.stream().forEach(point -> timePoints.add(
                new TimePoint(Double.parseDouble(point.getUserData().toString()),
                                new Point2D.Double(point.getX(), point.getY()))
                ));

        model.addAttribute("points", timePoints);
        return "map";
    }
}
