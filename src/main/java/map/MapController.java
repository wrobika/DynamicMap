package map;

import com.vividsolutions.jts.geom.*;
import com.vividsolutions.jts.geom.util.GeometryExtracter;
import com.vividsolutions.jts.io.WKTReader;
import org.apache.commons.math3.util.Precision;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.spark.SparkConf;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static map.GridController.getEmptyGrid;
import static map.GridController.getTimeGrid;
import static osrm.UpdateController.getModifiedRoads;
import static osrm.UpdateController.updateRoads;

@Controller
public class MapController {

    private static String measures = "/dynamicmap/measures";

    @GetMapping("/map2")
    public String map2(@RequestParam(required=false) List<String> coordinates, Model model)
    {
        GeometryFactory geometryFactory = new GeometryFactory();
        Map<Point, Double> timePoints;
        List<Point> ambulances = new ArrayList<>();
        for (int i = 0; i < coordinates.size(); i += 2) {
            Double x = Double.valueOf(coordinates.get(i));
            Double y = Double.valueOf(coordinates.get(i + 1));
            x = Precision.round(x, 6);
            y = Precision.round(y, 6);
            Coordinate roundCoord = new Coordinate(x, y);
            ambulances.add(geometryFactory.createPoint(roundCoord));
        }
        try
        {
            timePoints = getTimeGrid();
        }
        catch(Exception ex)
        {
            ex.printStackTrace();
            timePoints = getEmptyGrid(true);
        }
        Application.ambulances = ambulances;
        model.addAttribute("points", timePoints);
        return "map";
    }

    @GetMapping("/map")
    public String map(Model model)
    {
        Map<Point, Double> timePoints = getEmptyGrid(true);
        model.addAttribute("points", timePoints);
        try
        {
            List<String> modifiedRoads = getModifiedRoads();
            model.addAttribute("roads", modifiedRoads);
        }
        catch(Exception ex)
        {
            ex.printStackTrace();
        }
        return "map";
    }

    @RequestMapping(value = "/grid", method=RequestMethod.POST)
    public @ResponseBody
    Map<Point, Double> grid(@RequestBody String stringAmbulancePoints)
    {
        long start = new Date().getTime();
        GeometryFactory geometryFactory = new GeometryFactory();
        List<Point> ambulances = new ArrayList<>();
        WKTReader wktReader = new WKTReader();
        Map<Point, Double> timeGrid;
        try
        {
            Geometry geometry = wktReader.read(stringAmbulancePoints);
            if(geometry instanceof GeometryCollection)
            {
                List<Point> points = new ArrayList<>();
                GeometryExtracter pointFilter = new GeometryExtracter(Point.class, points);
                geometry.apply(pointFilter);
                for (Point point : points) {
                    Coordinate coord = point.getCoordinate();
                    Double x = Precision.round(coord.x, 6);
                    Double y = Precision.round(coord.y, 6);
                    Coordinate roundCoord = new Coordinate(x, y);
                    ambulances.add(geometryFactory.createPoint(roundCoord));
                }
            }
            else if(geometry instanceof Point)
            {
                Double x = Precision.round(geometry.getCoordinate().x, 6);
                Double y = Precision.round(geometry.getCoordinate().y, 6);
                Coordinate roundCoord = new Coordinate(x,y);
                ambulances.add(geometryFactory.createPoint(roundCoord));
            }
            Application.ambulances = ambulances;
            timeGrid = getTimeGrid();
            long stop = new Date().getTime();
	    System.out.println("\n\n\n");
	    System.out.println((stop-start)/60000.0);
	    System.out.println("\n\n\n");
            saveTime(start,stop);
        }
        catch(Exception ex)
        {
            ex.printStackTrace();
            timeGrid = getEmptyGrid(true);
        }
        return timeGrid;
    }

    @RequestMapping(value = "/update", method=RequestMethod.POST)
    public @ResponseBody
    Map<Point, Double> update(@RequestBody String roadToUpdate)
    {
        try
        {
            long start = new Date().getTime();
            WKTReader wktReader = new WKTReader();
            Geometry geometry = wktReader.read(roadToUpdate);
            List<Coordinate> coordinates = Arrays.asList(geometry.getCoordinates());
            updateRoads(coordinates);
            long stop = new Date().getTime();
            saveTime(start,stop);
            return getTimeGrid();
        }
        catch(Exception ex)
        {
            ex.printStackTrace();
            return getEmptyGrid(true);
        }
    }

    private static void saveTime(long start, long stop) throws IOException
    {
        String id = Application.sc.getConf().getAppId();
        String timeFile = measures + "/n2c4_" +id;
        FileSystem hdfs = Application.hdfs;
        Path path = new Path(measures);
        if(!hdfs.exists(path))
            hdfs.mkdirs(path);
        Path timeFilePath = new Path(timeFile);
        FSDataOutputStream outputStream = hdfs.create(timeFilePath);
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream));
        writer.append(String.valueOf((stop-start)/60000.0));
        writer.append("\n");
        writer.flush();
        writer.close();
    }
}
