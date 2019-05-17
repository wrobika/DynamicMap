package map;

import java.awt.geom.Point2D;

public class TimePoint
{
    private double time;
    private Point2D.Double destination;

    public TimePoint()
    {
        time = 9.4;
        destination = new Point2D.Double(50.3,20.1);
    }

    public TimePoint(double newTime, Point2D.Double newDestination)
    {
        time = newTime;
        destination = newDestination;
    }

    public double getTime()
    {
        return time;
    }

    public Point2D.Double getDestination()
    {
        return destination;
    }

    public double getLatitude()
    {
        return destination.getX();
    }


    public double getLongitude()
    {
        return destination.getY();
    }

    public void setTime(double newTime)
    {
        time = newTime;
    }

    public void setDestination(Point2D.Double newDestination)
    {
        destination = newDestination;
    }
}
