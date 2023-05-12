
package it.cnr.isti.steplogger;

// Declare any non-default types here with import statements
interface IStepLoggerService {

    /** external apps must provide their position update every 500 ms using this method */
    void logPosition(in long timestamp, in double x, in double y, in double z);

    /** let the service start a new measurement session. this will create an always-on-top button to log waypoints */
    void startNewSession(in String uid);

}
