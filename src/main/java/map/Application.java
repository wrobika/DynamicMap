package map;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.CoordinateSequence;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.serializer.KryoSerializer;
import org.datasyslab.geospark.serde.GeoSparkKryoRegistrator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.awt.geom.Point2D;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import osrm.OsrmController;

import static java.lang.Math.cos;
import static map.PointGridController.createGrid;

@SpringBootApplication
public class Application {

    public static JavaSparkContext sc;

    public static void main(String[] args) {

        SpringApplication.run(Application.class, args);

        SparkConf conf = new SparkConf().setAppName("DynamicMap")
            .setMaster("local[*]")
            .set("spark.serializer", KryoSerializer.class.getName())
            .set("spark.kryo.registrator", GeoSparkKryoRegistrator.class.getName());
        sc = new JavaSparkContext(conf);

        //createGrid();

        GeometryFactory geometryFactory = new GeometryFactory();
        Point startPoint = geometryFactory.createPoint(new Coordinate(19.960292,50.021329));
        OsrmController.downloadRoutesFromPoint(startPoint);
    }
}
