package com.udacity.security.service;

import static com.udacity.security.data.AlarmStatus.ALARM;
import static com.udacity.security.data.AlarmStatus.NO_ALARM;
import static com.udacity.security.data.AlarmStatus.PENDING_ALARM;
import static com.udacity.security.data.ArmingStatus.ARMED_HOME;
import static com.udacity.security.data.ArmingStatus.DISARMED;

import com.udacity.image.service.ImageService;
import com.udacity.security.application.StatusListener;
import com.udacity.security.data.AlarmStatus;
import com.udacity.security.data.ArmingStatus;
import com.udacity.security.data.SecurityRepository;
import com.udacity.security.data.Sensor;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * Service that receives information about changes to the security system. Responsible for
 * forwarding updates to the repository and making any decisions about changing the system state.
 * <p>
 * This is the class that should contain most of the business logic for our system, and it is the
 * class you will be writing unit tests for.
 */
public class SecurityService {

  private final ImageService imageService;
  private final SecurityRepository securityRepository;
  private final Set<StatusListener> statusListeners = new HashSet<>();

  private boolean defaultCatDetectionFlag = false;

  public SecurityService(SecurityRepository securityRepository, ImageService imageService) {
    this.securityRepository = securityRepository;
    this.imageService = imageService;
  }

  /**
   * Sets the current arming status for the system. Changing the arming status may update both the
   * alarm status.
   *
   * @param armingStatus
   */
  public void setArmingStatus(ArmingStatus armingStatus) {
    if (defaultCatDetectionFlag && armingStatus == ARMED_HOME) {
      setAlarmStatus(ALARM);
    }

    if (armingStatus == DISARMED) {
      setAlarmStatus(NO_ALARM);
    } else {
      ConcurrentSkipListSet<Sensor> sensors = new ConcurrentSkipListSet<>(getSensors());
      sensors.forEach(sensor -> changeSensorActivationStatus(sensor, false));
    }

    securityRepository.setArmingStatus(armingStatus);
    statusListeners.forEach(StatusListener::sensorStatusChanged);
  }

  /**
   * Internal method that handles alarm status changes based on whether the camera currently shows a
   * cat.
   *
   * @param cat True if a cat is detected, otherwise false.
   */
  private void catDetected(Boolean cat) {
    defaultCatDetectionFlag = cat;
    if (cat && getArmingStatus() == ARMED_HOME) {
      setAlarmStatus(AlarmStatus.ALARM);
    } else if (!cat && getAllSensorsFromState(false)){
      setAlarmStatus(NO_ALARM);
    }

    statusListeners.forEach(statusListener -> statusListener.catDetected(cat));
  }

  /**
   * Register the StatusListener for alarm system updates from within the SecurityService.
   *
   * @param statusListener
   */
  public void addStatusListener(StatusListener statusListener) {
    statusListeners.add(statusListener);
  }

  public void removeStatusListener(StatusListener statusListener) {
    statusListeners.remove(statusListener);
  }

  /**
   * Change the alarm status of the system and notify all listeners.
   *
   * @param status
   */
  public void setAlarmStatus(AlarmStatus status) {
    securityRepository.setAlarmStatus(status);
    statusListeners.forEach(statusListener -> statusListener.notify(status));
  }

  /**
   * Internal method for updating the alarm status when a sensor has been activated.
   */
  private void handleSensorActivated() {
    if (DISARMED.equals(getArmingStatus())) {
      return; //no problem if the system is disarmed
    }
    switch (getAlarmStatus()) {
      case NO_ALARM -> setAlarmStatus(PENDING_ALARM);
      case PENDING_ALARM -> setAlarmStatus(ALARM);
    }
  }

  /**
   * Internal method for updating the alarm status when a sensor has been deactivated
   */
  private void handleSensorDeactivated() {
    switch (getAlarmStatus()) {
      case PENDING_ALARM -> setAlarmStatus(NO_ALARM);
      case ALARM -> setAlarmStatus(PENDING_ALARM);
    }
  }

  /**
   * Change the activation status for the specified sensor and update alarm status if necessary.
   *
   * @param sensor
   * @param active
   */
  public void changeSensorActivationStatus(Sensor sensor, Boolean active) {
    if (!ALARM.equals(getAlarmStatus())) {
      if (active) {
        handleSensorActivated();
      } else if (sensor.getActive()) {
        handleSensorDeactivated();
      }
    }
    sensor.setActive(active);
    securityRepository.updateSensor(sensor);
  }

  /**
   * Send an image to the SecurityService for processing. The securityService will use its provided
   * ImageService to analyze the image for cats and update the alarm status accordingly.
   *
   * @param currentCameraImage
   */
  public void processImage(BufferedImage currentCameraImage) {
    catDetected(imageService.imageContainsCat(currentCameraImage, 50.0f));
  }

  private boolean getAllSensorsFromState(boolean state) {
    return getSensors().stream().allMatch(sensor -> sensor.getActive() == state);
  }

  public AlarmStatus getAlarmStatus() {
    return securityRepository.getAlarmStatus();
  }

  public Set<Sensor> getSensors() {
    return securityRepository.getSensors();
  }

  public void addSensor(Sensor sensor) {
    securityRepository.addSensor(sensor);
  }

  public void removeSensor(Sensor sensor) {
    securityRepository.removeSensor(sensor);
  }

  public ArmingStatus getArmingStatus() {
    return securityRepository.getArmingStatus();
  }
}
