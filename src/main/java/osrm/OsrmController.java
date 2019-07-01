package osrm;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.geotools.geojson.GeoJSON;
import org.geotools.geojson.geom.GeometryJSON;
import org.json.JSONArray;
import org.json.JSONObject;
import org.locationtech.jts.geom.Geometry;
import org.springframework.stereotype.Controller;

import java.awt.geom.Point2D;
import java.io.*;
import java.net.*;
import java.util.*;

@Controller
public class OsrmController
{
    public static void downloadRoutesFromPoint(Point2D.Double startPoint)
    {
        List<Point2D.Double> points = new ArrayList(Arrays.asList(startPoint));
        points.add(new Point2D.Double(20.122974,50.097975));
        String response = getRouteResponse(points);
        if(response != null)
        {
            JSONObject route = createRouteFromResponse(response);
            writeRouteToFile("test.json", route.toString());
        }

    }

    private static String getRouteResponse(List<Point2D.Double> points)
    {
        try {
            if(points.size() < 2)
                throw new Exception("too few points to set route");
            String pointsString = pointsToString(points);
            String url = "http://127.0.0.1:5000/route/v1/driving/" + pointsString + "?geometries=geojson";
            HttpClient client = HttpClientBuilder.create().build();
            HttpResponse response = client.execute(new HttpGet(url));
            HttpEntity entityResponse = response.getEntity();
            return EntityUtils.toString(entityResponse, "UTF-8");
        }
        catch(Exception ex)
        {
            System.out.println(ex.getMessage());
            return null;
        }
    }

    private static void writeRouteToFile(String fileName, String routeJSON)
    {
        try
        {
            BufferedWriter writer = new BufferedWriter(new FileWriter(fileName, true));
            writer.append(routeJSON);
            writer.append('\n');
            writer.close();
        }
        catch(IOException ex)
        {
            System.out.println(ex.getMessage());
        }
    }

    private static JSONObject createRouteFromResponse(String response)
    {
        JSONObject responseJSON = new JSONObject(response);
        JSONArray startPoint = responseJSON
                .getJSONArray("waypoints")
                .getJSONObject(0)
                .getJSONArray("location");
        JSONArray endPoint = responseJSON
                .getJSONArray("waypoints")
                .getJSONObject(1)
                .getJSONArray("location");
        Double time = responseJSON
                .getJSONArray("routes")
                .getJSONObject(0)
                .getDouble("duration");
        JSONObject geometry = responseJSON
                .getJSONArray("routes")
                .getJSONObject(0)
                .getJSONObject("geometry");
        return new JSONObject()
                .put("geometry", geometry)
                .put("properties", new JSONObject()
                        .put("time", time)
                        .put("start", startPoint)
                        .put("end", endPoint)
//                        .put("STATEFP", "01")
//                        .put("COUNTYFP", "089")
//                        .put("TRACTCE", "001700")
//                        .put("BLKGRPCE", "2")
//                        .put("AFFGEOID", "1500000US010890017002")
//                        .put("GEOID", "010890017002")
//                        .put("NAME", "2")
//                        .put("LSAD", "BG")
//                        .put("ALAND", 1040641)
//                        .put("AWATER", 0)
                )
                .put("type", "Feature");
    }

    private static String pointsToString(List<Point2D.Double> points)
    {
        StringBuilder result = new StringBuilder();

        for (Point2D.Double point : points) {
            result.append(point.x);
            result.append(",");
            result.append(point.y);
            result.append(";");
        }

        result.deleteCharAt(result.length()-1);
        String resultString = result.toString();
        return resultString.length() > 0 ? resultString.substring(0, resultString.length() - 1) : resultString;
    }
}
