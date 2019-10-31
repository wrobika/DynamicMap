package osrm;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;
import map.Application;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.apache.spark.api.java.JavaRDD;
import org.datasyslab.geospark.formatMapper.GeoJsonReader;
import org.datasyslab.geospark.spatialOperator.RangeQuery;
import org.datasyslab.geospark.spatialRDD.SpatialRDD;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static osrm.OsrmController.getHttpResponse;
import static osrm.OsrmController.pointsToString;

public class UpdateController
{
    public static List updateRoads(List<Point> points)
    {
        JSONObject responseJSON = new JSONObject();
        do
        {
            try
            {
                String response = getTripResponse(points);
                responseJSON = new JSONObject(response);
            }
            catch(Exception ex)
            {
                System.out.println(ex.getMessage());
            }
        } while (!responseJSON.has("trips"));
        List nodes = getRoadNodes(responseJSON);
        LineString road = getRoad(responseJSON);
        JavaRDD intersectedRoutesRDD = findIntersectedRoutes(road);

        if(!nodes.isEmpty())
        {
            writeUpdateFile(nodes);
        }
        return nodes;
    }

    private static String getTripResponse(List<Point> points) throws Exception
    {
        if(points.size() < 2)
            throw new Exception("too few points to set route");
        String pointsString = pointsToString(points);
        String path = "/trip/v1/driving/" + pointsString;
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

    private static JavaRDD findIntersectedRoutes(LineString road)
    {
        JavaRDD<LineString> allRoutesRDD = Application.sc.emptyRDD();
        List<JavaRDD<LineString>> routeRDDsList = new ArrayList<>();
        try (Stream<Path> routeFiles = Files.walk(Paths.get("")))
        {
            List<String> fileNames = routeFiles.map(Path::toString)
                    .filter(f -> f.startsWith("routes-"))
                    .collect(Collectors.toList());

            for (String file: fileNames)
            {
                SpatialRDD routesFromOnePoint = GeoJsonReader.readToGeometryRDD(Application.sc, file);
                routeRDDsList.add(routesFromOnePoint.rawSpatialRDD);
            }

            allRoutesRDD = Application.sc.union(allRoutesRDD, routeRDDsList);
            SpatialRDD<LineString> allRoutesSpatialRDD = new SpatialRDD<>();
            allRoutesSpatialRDD.setRawSpatialRDD(allRoutesRDD);
            return RangeQuery.SpatialRangeQuery(allRoutesSpatialRDD, road, true, false);
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return allRoutesRDD;
        }
    }
}
