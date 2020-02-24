package map;

import com.vividsolutions.jts.geom.Coordinate;
import org.apache.hadoop.fs.FileSystem;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.serializer.KryoSerializer;
import org.datasyslab.geospark.serde.GeoSparkKryoRegistrator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@SpringBootApplication
public class Application {

    public static JavaSparkContext sc;
    public static List<Coordinate> ambulanceCoordinates;
    public static FileSystem hdfs;

    public static void main(String[] args) throws IOException {

        SpringApplication.run(Application.class, args);

        SparkConf conf = new SparkConf().setAppName("DynamicMap")
            .setMaster("spark://master:7077")
            .set("spark.serializer", KryoSerializer.class.getName())
            .set("spark.kryo.registrator", GeoSparkKryoRegistrator.class.getName())
            .set("spark.hadoop.validateOutputSpecs", "false");

        sc = new JavaSparkContext(conf);
        ambulanceCoordinates = new ArrayList<>();
        hdfs = FileSystem.get(sc.hadoopConfiguration());

        //createGrid();

        //GeometryFactory geometryFactory = new GeometryFactory();
        //Point startPoint = geometryFactory.createPoint(new Coordinate(19.916150,50.091422));
        //DownloadController.downloadRoutesFromPoint(startPoint);
    }
}
