package map;

import com.vividsolutions.jts.geom.*;
import com.vividsolutions.jts.io.WKTReader;
import org.apache.commons.math3.util.Precision;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.text.DecimalFormat;
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
        GeometryFactory geometryFactory = new GeometryFactory();
        Map<Point, Double> timePoints;
        List<Point> ambulancePoints = new ArrayList<>();

        if(coordinates == null || coordinates.isEmpty())
        {
            Point point1 = geometryFactory.createPoint(new Coordinate(19.960292,50.021329));
            Point point2 = geometryFactory.createPoint(new Coordinate(20.00297035937211,49.97754568000001));
            Point point3 = geometryFactory.createPoint(new Coordinate(19.916150,50.091422));
            timePoints = getTimeGrid(Arrays.asList(point1, point2, point3));
        }
        else
        {
            for(int i = 0; i<coordinates.size(); i+=2)
            {
                Double x = Double.valueOf(coordinates.get(i));
                Double y = Double.valueOf(coordinates.get(i+1));
                x = Precision.round(x, 6);
                y = Precision.round(y, 6);
                Point point = geometryFactory.createPoint(new Coordinate(x,y));
                ambulancePoints.add(point);
            }
            timePoints = getTimeGrid(ambulancePoints);
        }
        model.addAttribute("points", timePoints);
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
    Map<Point, Double> grid(@RequestBody String ambulanceCoordinates)
    {
        List<Point> ambulancePoints = new ArrayList<>();
        try
        {
            GeometryFactory geometryFactory = new GeometryFactory();
            WKTReader wktReader = new WKTReader();
            Geometry geometry = wktReader.read(ambulanceCoordinates);
            if(geometry instanceof GeometryCollection)
            {
                Coordinate[] coordinates = geometry.getCoordinates();
                for (Coordinate coord : coordinates)
                {
                    Double x = Precision.round(coord.x, 6);
                    Double y = Precision.round(coord.y, 6);
                    Coordinate roundCoord = new Coordinate(x,y);
                    ambulancePoints.add(geometryFactory.createPoint(roundCoord));
                }
            }
            else if(geometry instanceof Point)
            {
                Double x = Precision.round(geometry.getCoordinate().x, 6);
                Double y = Precision.round(geometry.getCoordinate().y, 6);
                Coordinate roundCoord = new Coordinate(x,y);
                ambulancePoints.add(geometryFactory.createPoint(roundCoord));
            }
        }
        catch(Exception ex)
        {
            System.out.println(ex.getMessage());
        }
        return getTimeGrid(ambulancePoints);
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
        }
        catch(Exception ex)
        {
            System.out.println(ex.getMessage());
        }
        return getEmptyGrid(true);
    }
}
