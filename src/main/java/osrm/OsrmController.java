package osrm;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Controller;
import java.io.*;
import java.util.*;
import java.util.List;
import com.vividsolutions.jts.geom.Point;

import static map.GridController.fileName;
import static map.GridController.getPointGrid;

@Controller
public class OsrmController
{
    public static void downloadRoutesFromPoint(Point startPoint)
    {
        String fileName = fileName(startPoint);
        List<Point> pointGrid = getPointGrid();
        for(Point point : pointGrid)
        {
            List<Point> points = new ArrayList<>(Arrays.asList(startPoint));
            points.add(point);
            String response = getRouteResponse(points);
            if(response != null)
            {
                JSONObject route = createRouteFromResponse(response);
                writeRouteToFile(fileName, route.toString());
            }
        }
    }

    private static String getRouteResponse(List<Point> points)
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

    private static String pointsToString(List<Point> points)
    {
        StringBuilder result = new StringBuilder();

        for (Point point : points) {
            result.append(point.getX());
            result.append(",");
            result.append(point.getY());
            result.append(";");
        }

        result.deleteCharAt(result.length()-1);
        String resultString = result.toString();
        return resultString.length() > 0 ? resultString.substring(0, resultString.length() - 1) : resultString;
    }
}
