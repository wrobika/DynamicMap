package map;

import com.vividsolutions.jts.geom.*;
import com.vividsolutions.jts.geom.util.GeometryExtracter;
import com.vividsolutions.jts.io.WKTReader;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.math3.util.Precision;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.*;
import java.util.stream.Collectors;

import static map.GridController.getEmptyGrid;
import static map.GridController.getTimeGrid;
import static osrm.UpdateController.getModifiedRoads;
import static osrm.UpdateController.updateRoads;

@Controller
public class MapController {

    private static long start;
    private static long stop;
    private static String measures = "/dynamicmap/measures";
    private static GeometryFactory geometryFactory = new GeometryFactory();

    @GetMapping("/map")
    public String map(Model model) {
        Map<Point, Double> timePoints = getEmptyGrid(true);
        model.addAttribute("points", timePoints);
        try {
            List<String> modifiedRoads = getModifiedRoads();
            model.addAttribute("roads", modifiedRoads);
        } catch(Exception ex) {
            ex.printStackTrace();
        }
        return "map";
    }

    @RequestMapping(value = "/grid", method=RequestMethod.POST)
    public @ResponseBody
    Map<Point, Double> grid(@RequestBody String stringAmbulancePoints) {
        start = new Date().getTime();
        List<Point> ambulances = new ArrayList<>();
        WKTReader wktReader = new WKTReader();
        Map<Point, Double> timeGrid;
        try {
            Geometry geometry = wktReader.read(stringAmbulancePoints);
            if(geometry instanceof GeometryCollection) {
                List<Point> points = new ArrayList<>();
                GeometryExtracter pointFilter = new GeometryExtracter(Point.class, points);
                geometry.apply(pointFilter);
                for (Point point : points) {
                    ambulances.add(pointWithRoundedCoordinate(point));
                }
            }
            else if(geometry instanceof Point) {
                ambulances.add(pointWithRoundedCoordinate((Point)geometry));
            }
            Application.ambulances = ambulances;
            timeGrid = getTimeGrid();
            stop = new Date().getTime();
            saveTime(start,stop);
        } catch(Exception ex) {
            ex.printStackTrace();
            timeGrid = getEmptyGrid(true);
        }
        return timeGrid;
    }

    @RequestMapping(value = "/update", method=RequestMethod.POST)
    public @ResponseBody
    Map<Point, Double> update(@RequestBody String roadToUpdate) {
        try {
            start = new Date().getTime();
            WKTReader wktReader = new WKTReader();
            Geometry geometry = wktReader.read(roadToUpdate);
            List<Coordinate> coordinates = Arrays.asList(geometry.getCoordinates());
            updateRoads(coordinates);
            stop = new Date().getTime();
            saveTime(start,stop);
            return getTimeGrid();
        } catch(Exception ex) {
            ex.printStackTrace();
            return getEmptyGrid(true);
        }
    }

    @RequestMapping(value = "/gridSample", method=RequestMethod.GET)
    public @ResponseBody
    String gridSample(@RequestParam int size) {
        Map<Point, Double> grid = getEmptyGrid(true);
        List<Point> points = new ArrayList<>(grid.keySet());
        List<Point> roundedCoordPoints = points.stream()
                .map(MapController::pointWithRoundedCoordinate)
                .collect(Collectors.toList());
        List sample = Application.sc
                .parallelize(roundedCoordPoints)
                .take(size);
        return StringUtils.join(sample.toArray(), ",");
    }

    private static Point pointWithRoundedCoordinate(Point point){
        Double x = Precision.round(point.getCoordinate().x, 6);
        Double y = Precision.round(point.getCoordinate().y, 6);
        return geometryFactory.createPoint(new Coordinate(x,y));
    }

    private static void saveTime(long start, long stop) throws IOException {
        String timeFile = measures + "/time";
        FileSystem hdfs = Application.hdfs;
        Path path = new Path(measures);
        if(!hdfs.exists(path))
            hdfs.mkdirs(path);
        Path timeFilePath = new Path(timeFile);
	    FSDataOutputStream outputStream;
	    if(!hdfs.exists(timeFilePath))
            outputStream = hdfs.create(timeFilePath);
	    else
	        outputStream = hdfs.append(timeFilePath);
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream));
        writer.append(String.valueOf((stop-start)/1000.0));
        writer.append("\n");
        writer.flush();
        writer.close();
    }
}
