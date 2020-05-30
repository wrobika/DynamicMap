package tests;

import map.MapController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class TestController {
    private MapController map = new MapController();

    @RequestMapping(value = "/testDownload")
    public void testDownload(@RequestParam String stringAmbulancePoints) {
        String[] points = stringAmbulancePoints.split(",");
        for(int i = 0; i < points.length; i++)
            map.grid(points[i]);
    }

    @RequestMapping(value = "/testReadAll")
    public void testReadAll(@RequestParam String stringAmbulancePoints, 
	@RequestParam int howManyTimes) {
        for(int i = 0; i < howManyTimes; i++)
            map.grid(stringAmbulancePoints);
    }

    /*@RequestMapping(value = "/testReadIncrease")
    public void testReadIncrease(@RequestParam String stringAmbulancePoints) {
        String[] points = stringAmbulancePoints.split(",");
        for(int i = 0; i < points.length; i++) {
            map.grid(stringAmbulancePoints);:
    }*/

}
