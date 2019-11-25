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
    static final String tripServiceOSRM = "/trip/v1/driving/";
    static final String nearestServiceOSRM = "/nearest/v1/driving/";
    private static final String routeServiceOSRM = "/route/v1/driving/";
    static final String scriptUpdateOSRM = "./updateOSRM.sh";
    private static final String schemeOSRM = "http";
    private static final String hostOSRM = "127.0.0.1"; //"router.project-osrm.org"
    private static final int portOSRM = 5000;
    private static final String scriptStartOSRM = "./startOSRM.sh";

    static Geometry downloadOneRoute(Coordinate start, Coordinate end) throws Exception
    {
        List<Coordinate> startEndPoints = Arrays.asList(start, end);
        String response = getRouteResponse(startEndPoints);
        return createLineStringRoute(response);
    }

    //TODO: sprawdzic czy pobral cala siatke
    public static void downloadRoutes(List<Coordinate> startCoords) throws Exception
    {
        List<Point> gridPoints = getGrid(false);
        List<Coordinate> gridCoordinates = gridPoints.stream()
                .map(Point::getCoordinate)
                .collect(Collectors.toList());
        List<Geometry> newRoutes = new ArrayList<>();
        for (Coordinate start: startCoords)
        {
            for(Coordinate coordFromGrid : gridCoordinates)
            {
                List<Coordinate> startEndCoord = Arrays.asList(start, coordFromGrid);
                String response = getRouteResponse(startEndCoord);
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
        builder.setPort(portOSRM);
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

    private static String getRouteResponse(List<Coordinate> coordinates) throws Exception
    {
        if(coordinates.size() < 2)
            throw new Exception("too few points to set route");
        String pointsString = coordinatesToString(coordinates);
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
}
