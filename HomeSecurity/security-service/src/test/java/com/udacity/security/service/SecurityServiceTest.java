package com.udacity.security.service;

import static com.udacity.security.data.AlarmStatus.ALARM;
import static com.udacity.security.data.AlarmStatus.NO_ALARM;
import static com.udacity.security.data.AlarmStatus.PENDING_ALARM;
import static com.udacity.security.data.ArmingStatus.ARMED_AWAY;
import static com.udacity.security.data.ArmingStatus.ARMED_HOME;
import static com.udacity.security.data.ArmingStatus.DISARMED;
import static com.udacity.security.data.SensorType.DOOR;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.atMostOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.udacity.image.service.ImageService;
import com.udacity.security.application.StatusListener;
import com.udacity.security.data.AlarmStatus;
import com.udacity.security.data.ArmingStatus;
import com.udacity.security.data.SecurityRepository;
import com.udacity.security.data.Sensor;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SecurityServiceTest {

  private SecurityService securityService;
  private Sensor sensor;

  @Mock
  private SecurityRepository securityRepository;

  @Mock
  private ImageService imageService;

  @Mock
  private StatusListener statusListener;

  private static Stream<Arguments> sensorActiveStatus() {
    return Stream.of(
        Arguments.of(true),
        Arguments.of(false)
    );
  }

  private static Stream<Arguments> armingStatus() {
    return Stream.of(
        Arguments.of(ARMED_HOME),
        Arguments.of(ARMED_AWAY)
    );
  }

  private static Stream<Arguments> alarmStatus() {
    return Stream.of(
        Arguments.of(NO_ALARM),
        Arguments.of(ALARM),
        Arguments.of(PENDING_ALARM)
    );
  }

  @BeforeEach
  void init() {
    securityService = new SecurityService(securityRepository, imageService);
    sensor = new Sensor("test-sensor", DOOR);
  }

  // Scenario 1: If alarm is armed and a sensor becomes activated, put the system into pending alarm status.
  @ParameterizedTest
  @MethodSource("armingStatus")
  void whenAlarmArmedAndSensorActivated_pending(ArmingStatus armingStatus) {
    when(securityRepository.getArmingStatus()).thenReturn(armingStatus);
    when(securityRepository.getAlarmStatus()).thenReturn(NO_ALARM);
    securityService.changeSensorActivationStatus(sensor, true);
    verify(securityRepository, atMostOnce()).setAlarmStatus(PENDING_ALARM);
  }

  // Scenario 2: If alarm is armed and a sensor becomes activated and the system is already pending alarm, set the alarm status to alarm.
  @ParameterizedTest
  @MethodSource("armingStatus")
  void whenAlarmArmedAndSensorActivatedAndStatusPending_alarm(ArmingStatus armingStatus) {
    when(securityRepository.getArmingStatus()).thenReturn(armingStatus);
    when(securityRepository.getAlarmStatus()).thenReturn(PENDING_ALARM);
    securityService.changeSensorActivationStatus(sensor, true);
    verify(securityRepository, atMostOnce()).setAlarmStatus(ALARM);
  }

  // Scenario 3: If pending alarm and all sensors are inactive, return to no alarm state.
  @Test
  void whenPendingAlarmAndAllSensorsInactive_noAlarm() {
    when(securityRepository.getAlarmStatus()).thenReturn(PENDING_ALARM);
    sensor.setActive(false);
    securityService.changeSensorActivationStatus(sensor, false);
    verify(securityRepository, atMostOnce()).setAlarmStatus(NO_ALARM);
  }

  // Scenario 4: If alarm is active, change in sensor state should not affect the alarm state.
  @ParameterizedTest
  @MethodSource("sensorActiveStatus")
  void whenAlarmActiveIfSensorDeactivated_noSensorChange(boolean status) {
    when(securityRepository.getAlarmStatus()).thenReturn(ALARM);
    securityService.changeSensorActivationStatus(sensor, status);
    verify(securityRepository, never()).setAlarmStatus(any(AlarmStatus.class));
  }

  // Scenario 5: If a sensor is activated while already active and the system is in pending state, change it to alarm state.
  @Test
  void whenSensorActivatedAndStatusPending() {
    when(securityRepository.getAlarmStatus()).thenReturn(PENDING_ALARM);
    sensor.setActive(true);
    securityService.changeSensorActivationStatus(sensor, true);
    verify(securityRepository, times(1)).setAlarmStatus(ALARM);
  }

  // Scenario 6: If a sensor is deactivated while already inactive, make no changes to the alarm state.
  @ParameterizedTest
  @MethodSource("alarmStatus")
  void whenSensorDeactivatedAndStatusPending_noAlarmStateChange(AlarmStatus alarmStatus) {
    when(securityRepository.getAlarmStatus()).thenReturn(alarmStatus);
    sensor.setActive(false);
    securityService.changeSensorActivationStatus(sensor, false);
    verify(securityRepository, never()).setAlarmStatus(any(AlarmStatus.class));
  }

  // Scenario 7: If the image service identifies an image containing a cat while the system is armed-home, put the system into alarm status.
  @Test
  void whenImageServiceIdentifiesCat_alarm() {
    when(securityRepository.getArmingStatus()).thenReturn(ARMED_HOME);
    when(imageService.imageContainsCat(any(BufferedImage.class), anyFloat())).thenReturn(true);
    securityService.processImage(mock(BufferedImage.class));
    verify(securityRepository, atMostOnce()).setAlarmStatus(ALARM);
  }

  // Scenario 8: If the image service identifies an image that does not contain a cat, change the status to no alarm as long as the sensors are not active.
  @Test
  void whenImageServiceIdentifiesNoCat_noAlarm() {
    when(imageService.imageContainsCat(any(BufferedImage.class), anyFloat())).thenReturn(false);
    securityService.processImage(mock(BufferedImage.class));
    verify(securityRepository, atMostOnce()).setAlarmStatus(NO_ALARM);
  }

  // Scenario 9: If the system is disarmed, set the status to no alarm.
  @Test
  void whenSystemDisarmed_noAlarm() {
    securityService.setArmingStatus((DISARMED));
    verify(securityRepository, atMostOnce()).setAlarmStatus(NO_ALARM);
  }

  // Scenario 10: If the system is armed, reset all sensors to inactive.
  @ParameterizedTest
  @MethodSource("armingStatus")
  void whenSystemArmed_resetSensors(ArmingStatus armingStatus) {
    when(securityRepository.getAlarmStatus()).thenReturn(PENDING_ALARM);
    when(securityRepository.getSensors()).thenReturn(
        new HashSet<>(List.of(
                new Sensor("test-sensor1", DOOR),
                new Sensor("test-sensor2", DOOR)
            )));
    securityService.setArmingStatus(armingStatus);
    securityService.getSensors().forEach(sensor -> assertFalse(sensor.getActive()));
  }

  // Scenario 11: If the system is armed-home while the camera shows a cat, set the alarm status to alarm.
  @Test
  void whenCameraShowsCat_alarm() {
    when(securityRepository.getArmingStatus()).thenReturn(DISARMED);
    when(imageService.imageContainsCat(any(BufferedImage.class), anyFloat())).thenReturn(true);
    securityService.processImage(mock(BufferedImage.class));
    securityService.setArmingStatus(ARMED_HOME);
    verify(securityRepository, atMostOnce()).setAlarmStatus(ALARM);
  }

  // Extra Test 1
  @Test
  void toggleStatusListener() {
    securityService.addStatusListener(statusListener);
    securityService.removeStatusListener(statusListener);
  }

  // Extra Test 2
  @Test
  void addRemoveSensors() {
    securityService.addSensor(sensor);
    securityService.removeSensor(sensor);
  }

  // Extra Test 3
  @Test
  void whenSensorActiveAndPendingAlarm() {
    when(securityRepository.getAlarmStatus()).thenReturn(PENDING_ALARM);
    sensor.setActive(true);
    securityService.changeSensorActivationStatus(sensor, false);
    verify(securityRepository, times(1)).setAlarmStatus(NO_ALARM);
  }

  // Extra Test 4
  @Test
  void whenSensorActiveAndAlarm() {
    when(securityRepository.getAlarmStatus()).thenReturn(ALARM);
    sensor.setActive(true);
    securityService.changeSensorActivationStatus(sensor, false);
    verify(securityRepository, atMostOnce()).setAlarmStatus(PENDING_ALARM);
  }
}