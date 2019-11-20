package osrm;

import com.vividsolutions.jts.geom.*;
import org.apache.spark.api.java.JavaRDD;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static map.RouteController.*;
import static osrm.OsrmController.*;

public class UpdateController
{
    public static void updateRoads(List<Coordinate> points)
    {
        try
        {
            String response = getTripResponse(points);
            JSONObject responseJSON = new JSONObject(response);
            List nodes = new ArrayList();
            if(responseJSON.get("code").equals("Ok"))
            {
                nodes = getRoadNodes(responseJSON);
            }
            else
            {
                //TODO: pobiranie pojedynczo nearest node
            }
            if(!nodes.isEmpty())
            {
                writeUpdateFile(nodes);
                restartOSRM();
                LineString road = getRoad(responseJSON);
                JavaRDD<Geometry> intersectedRoutesRDD = findIntersectedRoutes(road);
                JavaRDD<Geometry> newRoutesRDD = intersectedRoutesRDD.map(route ->
                    downloadOneRoute(getStartCoord(route), getEndCoord(route))
                );
                replaceRoutes(intersectedRoutesRDD, newRoutesRDD);
            }
        }
        catch(Exception ex)
        {
            ex.printStackTrace();
        }
    }

    private static String getTripResponse(List<Coordinate> coordinates) throws Exception
    {
        if(coordinates.size() < 2)
            throw new Exception("too few points to set route");
        String coordinatesString = coordinatesToString(coordinates);
        String path = "/trip/v1/driving/" + coordinatesString;
        Map<String, String> parameters = new HashMap<>();
        parameters.put("source","first");
        parameters.put("destination","last");
        parameters.put("annotations","nodes");
        parameters.put("roundtrip","false");
        parameters.put("geometries","geojson");
        return getHttpResponse(path,parameters);
    }

    private static List getRoadNodes(JSONObject responseJSON)
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

    private static void writeUpdateFile(List nodes)
    {
        try
        {
            FileWriter csvWriter = new FileWriter("update.csv");
            for (int i = 0; i<nodes.size()-1; i++) {
                csvWriter.append(nodes.get(i).toString());
                csvWriter.append(',');
                csvWriter.append(nodes.get(i+1).toString());
                csvWriter.append(",0");
                csvWriter.append("\n");
            }
            csvWriter.flush();
            csvWriter.close();
        }
        catch(IOException ex)
        {
            System.out.println(ex.getMessage());
        }
    }

    private static void restartOSRM()
    {
        try
        {
            Process process = Runtime.getRuntime().exec("./updateOSRM.sh");
            if (process.waitFor() != 0)
            {
                System.out.println(process.exitValue() + ": error while update road speed");
            }
        }
        catch(IOException | InterruptedException ex)
        {
            ex.printStackTrace();
        }
    }
}
