package map;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryCollection;
import com.vividsolutions.jts.io.WKTReader;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static map.GridController.getTimeGrid;

@Controller
public class MapController {

    @GetMapping("/map")
    public String map(@RequestParam(required=false) List<String> coordinates, Model model)
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
                Point point = geometryFactory.createPoint(new Coordinate(x,y));
                ambulancePoints.add(point);
            }
            timePoints = getTimeGrid(ambulancePoints);
        }
        model.addAttribute("points", timePoints);
        return "map";
    }

    @RequestMapping(value = "/grid", method=RequestMethod.POST)
    public @ResponseBody
    Map<Point, Double> grid(@RequestBody String ambulanceCoordinates)
    {
        Map<Point, Double> timePoints;
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
                    ambulancePoints.add(geometryFactory.createPoint(coord));
                }
            }
            else if(geometry instanceof Point)
            {
                ambulancePoints.add(geometryFactory.createPoint(geometry.getCoordinate()));
            }
        }
        catch(Exception ex)
        {
            System.out.println(ex.getMessage());
        }
        timePoints = getTimeGrid(ambulancePoints);
        return timePoints;
    }
}
