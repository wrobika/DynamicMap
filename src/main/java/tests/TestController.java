package tests;

import map.MapController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class TestController {
    private MapController map = new MapController();

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
}
