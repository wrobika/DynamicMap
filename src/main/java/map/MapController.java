package map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.awt.geom.Point2D;
import java.util.ArrayList;

@Controller
public class MapController {

    @GetMapping("/map")
    public String map(Model model) {
        ArrayList<TimePoint> points = new ArrayList<>(2);
        points.add(new TimePoint(13.6, new Point2D.Double(50.1,20.2)));
        points.add(new TimePoint(4.8, new Point2D.Double(49.90,20.2)));
        model.addAttribute("points", points);
        return "map";
    }

}
