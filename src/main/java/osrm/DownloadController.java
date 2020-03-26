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
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.stereotype.Controller;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.List;

import static map.GridController.getGrid;
import static map.RouteController.addNewRoutes;

@Controller
public class DownloadController
{
    public static final String startOSRM = "/start";
    static final String updateOSRM = "/update";
    static final String tripServiceOSRM = "/trip/v1/driving/";
    static final String nearestServiceOSRM = "/nearest/v1/driving/";
    private static final String routeServiceOSRM = "/route/v1/driving/";
    private static final String schemeOSRM = "http";
    private static final String hostOSRM = "osrm-4027.cloud.plgrid.pl"; //"router.project-osrm.org"
    private static final String hostManageOSRM = "osrm-manage-4027.cloud.plgrid.pl";

    public static void manageOSRM(String action) throws Exception
    {
        URL url = getURL(hostManageOSRM, action, new HashMap<>());
        HttpClient client = HttpClientBuilder.create().build();
        HttpResponse response = client.execute(new HttpGet(url.toString()));
        response.getStatusLine().getStatusCode();
	    pingOSRM();
    }

    public static void downloadAllRoutes() throws Exception
    {
        List<Point> gridPoints = getGrid(true);
        downloadRoutes(gridPoints);
    }

    //TODO: sprawdzic czy pobral cala siatke
    public static void downloadRoutes(List<Point> startPoints) throws Exception
    {
        List<Point> gridPoints = getGrid(false);
        List<Geometry> newRoutes = new ArrayList<>();
        long size = startPoints.size();
        long count = 0;
        for (Point start: startPoints)
        {
            for(Point pointFromGrid : gridPoints)
            {
                List<Point> startEndPoints = Arrays.asList(start, pointFromGrid);
                String response = getRouteResponse(startEndPoints);
                LineString route = createLineStringRoute(response);
                newRoutes.add(route);
            }
            addNewRoutes(newRoutes);
            System.out.println("downloaded " + count++ +"/"+ size);
        }
    }

    static Geometry downloadOneRoute(Point start, Point end) throws Exception
    {
        List<Point> startEndPoints = Arrays.asList(start, end);
        String response = getRouteResponse(startEndPoints);
        return createLineStringRoute(response);
    }

    static String getHttpResponse(String path, Map<String, String> parameters) throws Exception
    {
        URL url = getURL(hostOSRM, path, parameters);
        HttpClient client = HttpClientBuilder.create().build();
        HttpResponse response = client.execute(new HttpGet(url.toString()));
        HttpEntity entityResponse = response.getEntity();
        String responseString = EntityUtils.toString(entityResponse, "UTF-8");
        if(!responseString.startsWith("{")) //to jest do wywalenia
        {
	        Thread.sleep(1000);
	        System.out.println(responseString);
            //manageOSRM(startOSRM);
            response = client.execute(new HttpGet(url.toString()));
            entityResponse = response.getEntity();
            responseString = EntityUtils.toString(entityResponse, "UTF-8");
        }
        return responseString;
    }

    private static void pingOSRM() throws Exception
    {
	    Thread.sleep(30000);
        URL url = getURL(hostOSRM, routeServiceOSRM, new HashMap<>());
        HttpClient client = HttpClientBuilder.create().build();
        HttpResponse response = client.execute(new HttpGet(url.toString()));
        HttpEntity entityResponse = response.getEntity();
        String responseString = EntityUtils.toString(entityResponse, "UTF-8");
        while(!responseString.startsWith("{"))
        {
            System.out.println("I am waiting for OSRM response");
            Thread.sleep(1000);
            response = client.execute(new HttpGet(url.toString()));
            entityResponse = response.getEntity();
            responseString = EntityUtils.toString(entityResponse, "UTF-8");
        }
    }

    private static URL getURL(String host, String path, Map<String, String> parameters) throws URISyntaxException, MalformedURLException
    {
        URIBuilder builder = new URIBuilder();
        builder.setScheme(schemeOSRM);
        builder.setHost(host);
        builder.setPath(path);
        parameters.forEach(builder::addParameter);
        return builder.build().toURL();
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
