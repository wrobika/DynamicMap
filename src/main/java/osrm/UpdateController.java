package osrm;

import com.vividsolutions.jts.geom.*;
import map.Application;
import org.apache.hadoop.fs.*;
import org.apache.spark.api.java.JavaRDD;
import org.json.JSONArray;
import org.json.JSONObject;
import scala.Tuple2;

import java.io.*;
import java.util.*;

import static map.RouteController.*;
import static osrm.DownloadController.*;

public class UpdateController
{
    private static final String modifiedRoadsLocation = "/dynamicmap/modifiedRoads";

    public static List<String> getModifiedRoads() throws IOException
    {
        List<String> modifiedRoads = new ArrayList<>();
        FileSystem hdfs = Application.hdfs;
        Path path = new Path(modifiedRoadsLocation);
        if(hdfs.exists(path))
        {
            FileStatus[] fileStatus = hdfs.listStatus(path);
            for (FileStatus file : fileStatus)
            {
                FSDataInputStream stream = hdfs.open(file.getPath());
                String firstLine = new BufferedReader(new InputStreamReader(stream)).readLine();
                String lineString = firstLine.substring
                        (firstLine.indexOf("LINESTRING"),
                                firstLine.length());
                modifiedRoads.add(lineString);
                stream.close();
            }
        }
        return modifiedRoads;
    }

    public static void updateRoads(List<Coordinate> roadCoordinates) throws Exception
    {
        Tuple2<LineString, List> roadAndNodes = getRoadNodes(roadCoordinates);
        List nodes = roadAndNodes._2;
        if(!nodes.isEmpty())
        {
            LineString road = roadAndNodes._1;
            writeUpdateFile(road, nodes);
            manageOSRM(updateOSRM);
            JavaRDD<Geometry> intersectedRoutesRDD = findIntersectedRoutes(road);
            JavaRDD<Geometry> newRoutesRDD = intersectedRoutesRDD.map(route ->
                downloadOneRoute(getStartPoint(route), getEndPoint(route))
            );
	    intersectedRoutesRDD.collect()
                .forEach(route -> {
                    System.out.println(route.getCoordinates()[route.getCoordinates().length-1]);
                    System.out.println(route.getUserData().toString());   
                });
            newRoutesRDD.collect()
                .forEach(route -> {
                    System.out.println(route.getCoordinates()[route.getCoordinates().length-1]);
                    System.out.println(route.getUserData().toString());
                });
            replaceRoutes(intersectedRoutesRDD, newRoutesRDD);
        }
    }

    private static Tuple2<LineString, List> getRoadNodes(List<Coordinate> roadCoordinates) throws Exception
    {
        String response = getTripResponse(roadCoordinates);
        JSONObject responseJSON = new JSONObject(response);
        List nodes = new ArrayList();
        LineString road;
        if(responseJSON.get("code").equals("Ok"))
        {
            nodes = getTripNodes(responseJSON);
            road = getRoad(responseJSON);
        }
        else
        {
            Coordinate[] realRoadCoordinates = new Coordinate[roadCoordinates.size()];
            for(int i =0; i<roadCoordinates.size(); i++)
            {
                response = getNearestResponse(roadCoordinates.get(i));
                responseJSON = new JSONObject(response);
                Tuple2<Integer, Coordinate> nodeAndCoord = getNearestNodeAndRealCoord(responseJSON);
                nodes.add(nodeAndCoord._1);
                realRoadCoordinates[i] = nodeAndCoord._2;
            }
            GeometryFactory geometryFactory = new GeometryFactory();
            road = geometryFactory.createLineString(realRoadCoordinates);
        }
        return new Tuple2<>(road, nodes);
    }

    private static String getTripResponse(List<Coordinate> coordinates) throws Exception
    {
        if(coordinates.size() < 2)
            throw new Exception("too few points to set route");
        String coordinatesString = coordinatesToString(coordinates);
        String path = tripServiceOSRM + coordinatesString;
        Map<String, String> parameters = new HashMap<>();
        parameters.put("source","first");
        parameters.put("destination","last");
        parameters.put("annotations","nodes");
        parameters.put("roundtrip","false");
        parameters.put("geometries","geojson");
        return getHttpResponse(path,parameters);
    }

    private static List getTripNodes(JSONObject responseJSON)
    {
        ArrayList nodeIds = new ArrayList();
        JSONArray lines = responseJSON
                .getJSONArray("trips")
                .getJSONObject(0)
                .getJSONArray("legs");
        for (int i=0; i < lines.length(); i++) {
            List oneLineNodes = lines.getJSONObject(i)
                    .getJSONObject("annotation")
                    .getJSONArray("nodes")
                    .toList();
            nodeIds.addAll(oneLineNodes);
        }
        return nodeIds;
    }

    private static LineString getRoad(JSONObject responseJSON)
    {
        GeometryFactory geometryFactory = new GeometryFactory();
        JSONArray geoJSONcoords = responseJSON
                .getJSONArray("trips")
                .getJSONObject(0)
                .getJSONObject("geometry")
                .getJSONArray("coordinates");
        Coordinate[] coordinates = new Coordinate[geoJSONcoords.length()];
        for(int i=0; i<geoJSONcoords.length(); i++)
        {
            Object object = geoJSONcoords.get(i);
            if(object instanceof JSONArray)
            {
                double x = ((JSONArray) object).getDouble(0);
                double y = ((JSONArray) object).getDouble(1);
                coordinates[i] = new Coordinate(x,y);
            }
        }
        return geometryFactory.createLineString(coordinates);
    }

    private static String getNearestResponse(Coordinate coordinates) throws Exception
    {
        String coordinatesString = coordinatesToString(Collections.singletonList(coordinates));
        String path = nearestServiceOSRM + coordinatesString;
        Map<String, String> parameters = new HashMap<>();
        return getHttpResponse(path,parameters);
    }

    private static Tuple2<Integer, Coordinate> getNearestNodeAndRealCoord(JSONObject responseJSON) throws Exception
    {
        int nodeId = responseJSON
                .getJSONArray("waypoints")
                .getJSONObject(0)
                .getJSONArray("nodes")
                .getInt(0);
        if(nodeId < 0)
            throw new Exception("cannot find nearest way nodes for route");
        JSONArray coords = responseJSON
                .getJSONArray("waypoints")
                .getJSONObject(0)
                .getJSONArray("location");
        double x = coords.getDouble(0);
        double y = coords.getDouble(1);
        return new Tuple2<>(nodeId, new Coordinate(x,y));
    }

    private static void writeUpdateFile(LineString road, List nodes) throws IOException
    {
        FileSystem hdfs = FileSystem.get(Application.sc.hadoopConfiguration());
        Path path = new Path(modifiedRoadsLocation);
        if(!hdfs.exists(path))
            hdfs.mkdirs(path);
        String updateFile = modifiedRoadsLocation + "/" +
                "LINESTRING" + String.valueOf(road.hashCode()) + ".csv";
        Path updateFilePath = new Path(updateFile);
        FSDataOutputStream outputStream = hdfs.create(updateFilePath);
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream));
        for (int i = 0; i<nodes.size()-1; i++) {
            writer.append(nodes.get(i).toString());
            writer.append(',');
            writer.append(nodes.get(i+1).toString());
            writer.append(",0,,");
            if(i==0) writer.append(road.toString());
            writer.append("\n");
        }
        writer.flush();
        writer.close();
    }
}
