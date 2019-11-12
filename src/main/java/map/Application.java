package map;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.serializer.KryoSerializer;
import org.datasyslab.geospark.serde.GeoSparkKryoRegistrator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

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

        //GeometryFactory geometryFactory = new GeometryFactory();
        //Point startPoint = geometryFactory.createPoint(new Coordinate(19.916150,50.091422));
        //OsrmController.downloadRoutesFromPoint(startPoint);
    }
}
