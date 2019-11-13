package osrm;

import map.GridController;
import map.RouteController;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Controller;
import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.List;

import com.vividsolutions.jts.geom.Point;
import static map.GridController.getGrid;

@Controller
public class OsrmController
{
    //TODO: sprawdzic czy pobral cala siatke
    public static void downloadRoutes(List<Point> ambulancePoints)
    {
        List<Point> gridPoints = getGrid(false);
        for (Point ambulance: ambulancePoints)
        {
            for(Point pointFromGrid : gridPoints)
            {
                List<Point> startEndPoints = Arrays.asList(ambulance, pointFromGrid);
                try
                {
                    String response = getRouteResponse(startEndPoints);
                    JSONObject route = createRouteFromResponse(response);
                    writeRouteToFile(RouteController.allRoutesLocation, route.toString());
                }
                catch(Exception ex)
                {
                    System.out.println(ex.getMessage());
                }
            }
        }
    }

    static String getHttpResponse(String path, Map<String, String> parameters)
    {
        //TODO: stop, gdy nie uruchomiony serwer osrm
        //osrm-routed --algorithm=MLD malopolskie-latest.osrm
        try
        {
            URIBuilder builder = new URIBuilder();
            builder.setScheme("http");
            builder.setHost("127.0.0.1");
            builder.setPort(5000);
            //builder.setHost("router.project-osrm.org");
            builder.setPath(path);
            parameters.forEach(builder::addParameter);
            URL url = builder.build().toURL();

            HttpClient client = HttpClientBuilder.create().build();
            HttpResponse response = client.execute(new HttpGet(url.toString()));
            HttpEntity entityResponse = response.getEntity();
            return EntityUtils.toString(entityResponse, "UTF-8");
        }
        catch(Exception ex)
        {
            System.out.println(ex.getMessage());
            return "";
        }
    }

    private static String getRouteResponse(List<Point> points) throws Exception
    {
        if(points.size() < 2)
            throw new Exception("too few points to set route");
        String pointsString = pointsToString(points);
        String path = "/route/v1/driving/" + pointsString;
        Map<String, String> parameters = new HashMap<>();
        parameters.put("geometries","geojson");
        return getHttpResponse(path,parameters);
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
                )
                .put("type", "Feature");
    }

    static String pointsToString(List<Point> points)
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
