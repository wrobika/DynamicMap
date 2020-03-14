package osrm;

import com.vividsolutions.jts.geom.*;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.geotools.geojson.geom.GeometryJSON;
import org.json.JSONObject;
import org.springframework.stereotype.Controller;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

import static map.GridController.getGrid;
import static map.RouteController.addNewRoutes;

@Controller
public class DownloadController
{
    public static final String scriptStartOSRM = "./startOSRM.sh";
    public static final String scriptUpdateOSRM = "./updateOSRM.sh";
    static final String tripServiceOSRM = "/trip/v1/driving/";
    static final String nearestServiceOSRM = "/nearest/v1/driving/";
    private static final String routeServiceOSRM = "/route/v1/driving/";
    private static final String schemeOSRM = "http";
    private static final String hostOSRM = "osrm-4027.cloud.plgrid.pl"; //"router.project-osrm.org"

    static Geometry downloadOneRoute(Point start, Point end) throws Exception
    {
        List<Point> startEndPoints = Arrays.asList(start, end);
        String response = getRouteResponse(startEndPoints);
        return createLineStringRoute(response);
    }

    //TODO: sprawdzic czy pobral cala siatke
    public static void downloadRoutes(List<Point> startPoints) throws Exception
    {
        List<Point> gridPoints = getGrid(false);
        List<Geometry> newRoutes = new ArrayList<>();
        for (Point start: startPoints)
        {
            for(Point pointFromGrid : gridPoints)
            {
                List<Point> startEndPoints = Arrays.asList(start, pointFromGrid);
                String response = getRouteResponse(startEndPoints);
                LineString route = createLineStringRoute(response);
                newRoutes.add(route);
            }
        }
        addNewRoutes(newRoutes);
    }

    static String getHttpResponse(String path, Map<String, String> parameters) throws Exception
    {
        URIBuilder builder = new URIBuilder();
        builder.setScheme(schemeOSRM);
        builder.setHost(hostOSRM);
        builder.setPath(path);
        parameters.forEach(builder::addParameter);
        URL url = builder.build().toURL();
        HttpClient client = HttpClientBuilder.create().build();
        try
        {
            HttpResponse response = client.execute(new HttpGet(url.toString()));
            HttpEntity entityResponse = response.getEntity();
            return EntityUtils.toString(entityResponse, "UTF-8");
        }
        catch (HttpHostConnectException ex)
        {
            Runtime.getRuntime().exec(scriptStartOSRM);
            if(!isReachableOSRM(url))
                throw ex;
        }
        HttpResponse response = client.execute(new HttpGet(url.toString()));
        HttpEntity entityResponse = response.getEntity();
        return EntityUtils.toString(entityResponse, "UTF-8");
    }

    private static boolean isReachableOSRM(URL url) throws InterruptedException, IOException
    {
        HttpClient client = HttpClientBuilder.create().build();
        int pingCount=0;
        while(pingCount<10) {
            try {
                client.execute(new HttpGet(url.toString()));
                return true;
            } catch (HttpHostConnectException ex) {
                Thread.sleep(1000);
                pingCount++;
            }
        }
        return false;
    }

    private static String getRouteResponse(List<Point> points) throws Exception
    {
        if(points.size() < 2)
            throw new Exception("too few points to set route");
        String pointsString = pointsToString(points);
        String path = routeServiceOSRM + pointsString;
        Map<String, String> parameters = new HashMap<>();
        parameters.put("geometries","geojson");
        return getHttpResponse(path,parameters);
    }

    private static LineString createLineStringRoute(String response) throws IOException
    {
        JSONObject responseJSON = new JSONObject(response);
        Double time = responseJSON
                .getJSONArray("routes")
                .getJSONObject(0)
                .getDouble("duration");
        String geometryString = responseJSON
                .getJSONArray("routes")
                .getJSONObject(0)
                .getJSONObject("geometry")
                .toString();
        GeometryJSON geometryJSON = new GeometryJSON();
        LineString route = geometryJSON.readLine(geometryString);
        route.setUserData(time);
        return route;
    }

    static String coordinatesToString(List<Coordinate> coordinates)
    {
        StringBuilder result = new StringBuilder();
        for (Coordinate coordinate : coordinates) {
            result.append(coordinate.x);
            result.append(",");
            result.append(coordinate.y);
            result.append(";");
        }
        result.deleteCharAt(result.length()-1);
        return result.toString();
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
        return result.toString();
    }
}
