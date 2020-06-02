package tests;

import map.MapController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;

@Controller
public class TestController {
    private MapController map = new MapController();

    @RequestMapping(value = "/testOneByOne")
    @ResponseStatus(value = HttpStatus.OK)
    public void testOneByOne(@RequestParam String stringAmbulancePoints) {
        String[] points = stringAmbulancePoints.split(",");
        for(String point : points)
            map.grid(point);
    }

    @RequestMapping(value = "/testAll")
    @ResponseStatus(value = HttpStatus.OK)
    public void testAll(@RequestParam String stringAmbulancePoints, @RequestParam int howManyTimes) {
	String requestParam = "GEOMETRYCOLLECTION(" + stringAmbulancePoints + ")";
        for(int i = 0; i < howManyTimes; i++)
            map.grid(requestParam);
    }

    @RequestMapping(value = "/testIncrease")
    @ResponseStatus(value = HttpStatus.OK)
    public void testIncrease(@RequestParam String stringAmbulancePoints) {
        String[] points = stringAmbulancePoints.split(",");
        for(int i = 0; i < points.length; i++) {
            StringBuilder requestParam = new StringBuilder("GEOMETRYCOLLECTION(");
            for(int j = 0; j < i; j++){
                requestParam.append(points[j]);
                requestParam.append(",");
            }
	    requestParam.append(points[i]);
            requestParam.append(")");
            map.grid(requestParam.toString());
        }
    }

    @RequestMapping(value = "/testUpdate")
    @ResponseStatus(value = HttpStatus.OK)
    public void testUpdate(@RequestParam String roadToUpdate, @RequestParam int howManyTimes) {
        for(int i = 0; i < howManyTimes; i++)
            map.update(roadToUpdate);
    }
}
