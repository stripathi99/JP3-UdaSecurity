package com.udacity.security.service;

import com.udacity.image.service.ImageService;
import com.udacity.security.application.StatusListener;
import com.udacity.security.data.AlarmStatus;
import com.udacity.security.data.ArmingStatus;
import com.udacity.security.data.SecurityRepository;
import com.udacity.security.data.Sensor;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

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

  private boolean catDetected = false;

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
    if (armingStatus == ArmingStatus.DISARMED) {
      setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    if (catDetected && armingStatus == ArmingStatus.ARMED_HOME) {
      setAlarmStatus(AlarmStatus.ALARM);
    }

    if (armingStatus == ArmingStatus.ARMED_HOME || armingStatus == ArmingStatus.ARMED_AWAY) {
      resetAllSensors();
    }

    securityRepository.setArmingStatus(armingStatus);
    statusListeners.forEach(StatusListener::sensorStatusChanged);
  }

  private void resetAllSensors() {
    Set<Sensor> sensors = getSensors().stream().peek(s -> s.setActive(false)).collect(
        Collectors.toSet());
    sensors.forEach(securityRepository::updateSensor);
  }

  /**
   * Internal method that handles alarm status changes based on whether the camera currently shows a
   * cat.
   *
   * @param cat True if a cat is detected, otherwise false.
   */
  private void catDetected(Boolean cat) {
    catDetected = cat;
    if (cat && getArmingStatus() == ArmingStatus.ARMED_HOME) {
      setAlarmStatus(AlarmStatus.ALARM);
    } else {
      setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    statusListeners.forEach(sl -> sl.catDetected(cat));
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
    statusListeners.forEach(sl -> sl.notify(status));
  }

  /**
   * Internal method for updating the alarm status when a sensor has been activated.
   */
  private void handleSensorActivated() {
    if (getArmingStatus() == ArmingStatus.DISARMED) {
      return; //no problem if the system is disarmed
    }
    switch (getAlarmStatus()) {
      case NO_ALARM -> setAlarmStatus(AlarmStatus.PENDING_ALARM);
      case PENDING_ALARM -> setAlarmStatus(AlarmStatus.ALARM);
    }
  }

  /**
   * Internal method for updating the alarm status when a sensor has been deactivated
   */
  private void handleSensorDeactivated(boolean flag) {
      if (getAlarmStatus() == AlarmStatus.PENDING_ALARM) {
          if (flag) {
              setAlarmStatus(AlarmStatus.NO_ALARM);
          } else {
              setAlarmStatus(AlarmStatus.ALARM);
          }
      }
//    switch (getAlarmStatus()) {
//      case PENDING_ALARM -> setAlarmStatus(AlarmStatus.NO_ALARM);
//      case ALARM -> setAlarmStatus(AlarmStatus.PENDING_ALARM);
//    }
  }

  /**
   * Change the activation status for the specified sensor and update alarm status if necessary.
   *
   * @param sensor
   * @param active
   */
  public void changeSensorActivationStatus(Sensor sensor, Boolean active) {
    if (!sensor.getActive() && active) {
      sensor.setActive(true);
      handleSensorActivated();
    } else if (sensor.getActive() && !active) {
      sensor.setActive(false);
      handleSensorDeactivated(false);
    } else if (!sensor.getActive() && !active) {
        // do nothing
    } else {
        handleSensorDeactivated(false);
    }
    sensor.setActive(active);
    securityRepository.updateSensor(sensor);

    if (allSensorsInactive()) {
        handleSensorDeactivated(true);
    }
  }

    private boolean allSensorsInactive() {
        return getSensors().stream().noneMatch(Sensor::getActive);
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
