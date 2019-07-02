package map;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static map.GridController.getTimeGrid;

@Controller
public class MapController {

    @GetMapping("/map")
    public String map(Model model) {
        GeometryFactory geometryFactory = new GeometryFactory();
        Point point1 = geometryFactory.createPoint(new Coordinate(19.960292,50.021329));
        Point point2 = geometryFactory.createPoint(new Coordinate(20.00297035937211,49.97754568000001));
        Point point3 = geometryFactory.createPoint(new Coordinate(19.916150,50.091422));
        Map<Point, Double> timePoints = getTimeGrid(Arrays.asList(point1, point2, point3));
        model.addAttribute("points", timePoints);
        return "map";
    }
}
