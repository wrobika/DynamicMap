package osrm;

import com.vividsolutions.jts.geom.*;
import map.RouteController;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
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

@Controller
public class OsrmController
{
    static Geometry downloadOneRoute(Coordinate start, Coordinate end) throws Exception
    {
        List<Coordinate> startEndPoints = Arrays.asList(start, end);
        String response = getRouteResponse(startEndPoints);
        return createLineStringRoute(response);
    }

    //TODO: sprawdzic czy pobral cala siatke
    public static void downloadRoutes(List<Coordinate> startCoords)
    {
        List<Point> gridPoints = getGrid(false);
        List<Coordinate> gridCoordinates = gridPoints.stream()
                .map(Point::getCoordinate)
                .collect(Collectors.toList());
        for (Coordinate start: startCoords)
        {
            for(Coordinate coordFromGrid : gridCoordinates)
            {
                List<Coordinate> startEndCoord = Arrays.asList(start, coordFromGrid);
                try
                {
                    String response = getRouteResponse(startEndCoord);
                    LineString route = createLineStringRoute(response);
                    writeRouteToFile(RouteController.routesOSRMLocation, route);
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

    private static String getRouteResponse(List<Coordinate> coordinates) throws Exception
    {
        if(coordinates.size() < 2)
            throw new Exception("too few points to set route");
        String pointsString = coordinatesToString(coordinates);
        String path = "/route/v1/driving/" + pointsString;
        Map<String, String> parameters = new HashMap<>();
        parameters.put("geometries","geojson");
        return getHttpResponse(path,parameters);
    }

    private static void writeRouteToFile(String fileName, LineString route)
    {
        try
        {
            BufferedWriter writer = new BufferedWriter(new FileWriter(fileName, true));
            writer.append(route.toString());
            writer.append('\n');
            writer.close();
        }
        catch(IOException ex)
        {
            System.out.println(ex.getMessage());
        }
    }

    private static LineString createLineStringRoute(String response)
    {
        try
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
        catch(IOException ex)
        {
            ex.printStackTrace();
            //TODO: ojojojojojojojoj
            return null;
        }
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
        String resultString = result.toString();
        return resultString.length() > 0 ? resultString.substring(0, resultString.length() - 1) : resultString;
    }
}
