package osrm;

import com.vividsolutions.jts.geom.*;
import map.Application;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.geotools.geojson.geom.GeometryJSON;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.stereotype.Controller;
import scala.Tuple2;

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
    private static final String hostOSRM = "localhost:5000";
    private static Random rand = new Random();
    private static final String scriptUpdateOSRM = "/home/ubuntu/updateAllOSRM.sh";
    private static final String scriptStartOSRM = "/home/ubuntu/startAllOSRM.sh";
    private static final String scriptPingOSRM = "/home/ubuntu/pingOSRM.sh";

    public static void startOSRM() throws Exception
    {
        try
        {
            Runtime.getRuntime().exec(scriptStartOSRM);
	    pingOSRM();
        }
        catch(IOException ex)
        {
            ex.printStackTrace();
        }
    }

    public static void updateOSRM() throws Exception
    {
        try
        {
	    Runtime.getRuntime().exec(scriptUpdateOSRM);
	    Thread.sleep(30000);
	    pingOSRM();
        }
        catch(Exception ex)
        {
            ex.printStackTrace();
        }
    }

    public static void pingOSRM() throws Exception
    {
	Process process = Runtime.getRuntime().exec(scriptPingOSRM);
        while (process.waitFor() != 0)
        {
            System.out.println("I am waiting for OSRM response");
            Thread.sleep(1000);
            process = Runtime.getRuntime().exec(scriptPingOSRM);
        }
    }

    public static void downloadRoutes(List<Point> startPoints) throws Exception
    {
        List<Point> gridPoints = getGrid(false);
        for (Point startPoint : startPoints)
        {
            JavaRDD<Point> toDownloadRDD = Application.sc.parallelize(gridPoints);
            JavaRDD<Geometry> newRoutesRDD = toDownloadRDD.map(endPoint ->
                    downloadOneRoute(startPoint, endPoint));
	    newRoutesRDD.cache();	
            addNewRoutes(newRoutesRDD);
            newRoutesRDD = Application.sc.emptyRDD();
        }
    }

    static Geometry downloadOneRoute(Point start, Point end) throws Exception
    {
	Thread.sleep(38);
        List<Point> startEndPoints = Arrays.asList(start, end);
        String response = getRouteResponse(startEndPoints);
        try
        {
            return createLineStringRoute(response);
        }
        catch(JSONException ex)
        {
            return null;
        }
    }

    static String getHttpResponse(String path, Map<String, String> parameters) throws Exception
    {
        URL url = getURL(hostOSRM, path, parameters);
        HttpClient client = HttpClientBuilder.create().build();
        HttpResponse response = client.execute(new HttpGet(url.toString()));
        HttpEntity entityResponse = response.getEntity();
        String responseString = EntityUtils.toString(entityResponse, "UTF-8");
        return responseString;
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
