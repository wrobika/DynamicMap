package map;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import org.datasyslab.geospark.enums.FileDataSplitter;
import org.datasyslab.geospark.formatMapper.GeoJsonReader;
import org.datasyslab.geospark.spatialRDD.PointRDD;
import org.datasyslab.geospark.spatialRDD.SpatialRDD;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import static map.PointGridController.getTimePointGrid;
import static map.PointGridController.readPointFromJSON;

@Controller
public class MapController {

    @GetMapping("/map")
    public String map(Model model) {

        //List<TimePoint> timePoints = getTimePointGrid();

        GeometryFactory geometryFactory = new GeometryFactory();
        Point startPoint = geometryFactory.createPoint(new Coordinate(19.960292,50.021329));
        List<TimePoint> timePoints = readPointFromJSON(startPoint);
        model.addAttribute("points", timePoints);
        return "map";
    }
}
