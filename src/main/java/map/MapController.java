package map;

import com.vividsolutions.jts.geom.*;
import com.vividsolutions.jts.io.WKTReader;
import org.apache.commons.math3.util.Precision;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static map.GridController.getEmptyGrid;
import static map.GridController.getTimeGrid;
import static osrm.UpdateController.updateRoads;

@Controller
public class MapController {

    @GetMapping("/map2")
    public String map2(@RequestParam(required=false) List<String> coordinates, Model model)
    {
        try
        {
            Map<Point, Double> timePoints;
            List<Coordinate> ambulanceCoordinates = new ArrayList<>();
            for (int i = 0; i < coordinates.size(); i += 2) {
                Double x = Double.valueOf(coordinates.get(i));
                Double y = Double.valueOf(coordinates.get(i + 1));
                x = Precision.round(x, 6);
                y = Precision.round(y, 6);
                ambulanceCoordinates.add(new Coordinate(x, y));
            }
            Application.ambulanceCoordinates = ambulanceCoordinates;
            timePoints = getTimeGrid(ambulanceCoordinates);
            model.addAttribute("points", timePoints);
        }
        catch(IOException ex)
        {
            ex.printStackTrace();
        }
        return "map";
    }

    @GetMapping("/map")
    public String map(Model model)
    {
        Map<Point, Double> timePoints = getEmptyGrid(true);
        model.addAttribute("points", timePoints);
        return "map";
    }

    @RequestMapping(value = "/grid", method=RequestMethod.POST)
    public @ResponseBody
    Map<Point, Double> grid(@RequestBody String ambulancePoints)
    {
        List<Coordinate> ambulanceCoordinates = new ArrayList<>();
        try
        {
            WKTReader wktReader = new WKTReader();
            Geometry geometry = wktReader.read(ambulancePoints);
            if(geometry instanceof GeometryCollection)
            {
                Coordinate[] coordinates = geometry.getCoordinates();
                for (Coordinate coord : coordinates)
                {
                    Double x = Precision.round(coord.x, 6);
                    Double y = Precision.round(coord.y, 6);
                    Coordinate roundCoord = new Coordinate(x,y);
                    ambulanceCoordinates.add(roundCoord);
                }
            }
            else if(geometry instanceof Point)
            {
                Double x = Precision.round(geometry.getCoordinate().x, 6);
                Double y = Precision.round(geometry.getCoordinate().y, 6);
                Coordinate roundCoord = new Coordinate(x,y);
                ambulanceCoordinates.add(roundCoord);
            }
            Application.ambulanceCoordinates = ambulanceCoordinates;
            return getTimeGrid(ambulanceCoordinates);
        }
        catch(Exception ex)
        {
            ex.printStackTrace();
            return getEmptyGrid(true);
        }
    }

    @RequestMapping(value = "/update", method=RequestMethod.POST)
    public @ResponseBody
    Map<Point, Double> update(@RequestBody String roadToUpdate)
    {
        try
        {
            WKTReader wktReader = new WKTReader();
            Geometry geometry = wktReader.read(roadToUpdate);
            List<Coordinate> coordinates = Arrays.asList(geometry.getCoordinates());
            updateRoads(coordinates);
            return getTimeGrid(Application.ambulanceCoordinates);
        }
        catch(Exception ex)
        {
            ex.printStackTrace();
            return getEmptyGrid(true);
        }
    }
}
