package org.apache.tika.pipes.core.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OSUtils {
  private static final Logger LOG = LoggerFactory.getLogger(OSUtils.class);

  private static String OS = System.getProperty("os.name").toLowerCase();

  private static final String KILL_WINDOWS_TASK = "taskkill /F /PID %d";
  private static final String KILL_NIX_TASK = "kill -s TERM %d";

  public static boolean isWindows() {
    return (OS.indexOf("win") >= 0);
  }

  public static boolean isMac() {
    return (OS.indexOf("mac") >= 0);
  }

  /**
   * Gets command line for a given process.
   *
   * @param pid The PID of the process.
   * @return Command line for the process if it exists.
   */
  /**
   * Get process id's for a certain executable regardless of operating system.
   *
   * @param executablePathContains The path of the executable you want to get PID's of.
   * @return Set of command lines
   */
  public static List<String> getCommandLinesFor(String executablePathContains) {
    List<String> commandLines = Lists.newArrayList();
    if (isWindows()) {
      try {
        String line;
        String processCmd = System.getenv("windir") + "\\system32\\Wbem\\wmic process get commandline,processid";
        Process p = Runtime.getRuntime().exec(processCmd);
        LOG.debug("Getting windows process id's for {} processes with cmd \"{}\"", executablePathContains, processCmd);
        try (BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
          while ((line = input.readLine()) != null) {
            LOG.debug("wmic process line: {}", line);
            if (line.toLowerCase().contains(executablePathContains.toLowerCase())) {
              commandLines.add(line);
            }
          }
        }
      } catch (Exception e) {
        LOG.error("Couldn't get process list on windows", e);
      }
    } else if (isMac()) {
      try {
        String line;
        String processCmd = "ps aux";
        Process p = Runtime.getRuntime().exec(processCmd);
        LOG.debug("Getting mac process id's for processes command line containing {} with cmd \"{}\"",
            executablePathContains, processCmd);
        try (BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
          while ((line = input.readLine()) != null) {
            LOG.debug("ps aux line: {}", line);
            if (line.contains(executablePathContains)) {
              commandLines.add(line);
            }
          }
        }
      } catch (Exception e) {
        LOG.error("Couldn't get process list", e);
      }
    } else {
      try {
        String line;
        String processCmd = String.format("ps -eo pid,args");
        LOG.debug("Getting linux process id's where command line contains {} with cmd \"{}\"",
            executablePathContains, processCmd);
        Process p = Runtime.getRuntime().exec(processCmd);
        try (BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
          while ((line = input.readLine()) != null) {
            LOG.debug("ps line: {}", line);
            if (line.contains(executablePathContains)) {
              commandLines.add(line);
            }
          }
        }
      } catch (Exception e) {
        LOG.error("Couldn't get process list", e);
      }
    }
    return commandLines;
  }

  /**
   * Get java process id's for a certain executable regardless of operating system.
   *
   * @param executablePathContains The path of the executable you want to get PID's of.
   * @param parentPid              The parent PID of the parent.
   * @return Set of the PID's.
   */
  public static Set<Integer> getJavaProcessIds(String executablePathContains, Integer parentPid) {
    Set<Integer> processIds = new HashSet<>();
    if (isWindows()) {
      try {
        String line;
        String processCmd = System.getenv("windir") + "\\system32\\Wbem\\wmic process where " +
            "\"name like '%java%'\" get processid,commandline";
        Process p = Runtime.getRuntime().exec(processCmd);
        LOG.debug("Getting windows process id's for {} processes with cmd \"{}\"", executablePathContains, processCmd);
        try (BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
          while ((line = input.readLine()) != null) {
            LOG.debug("wmic process line: {}", line);
            if (line.toLowerCase().contains(executablePathContains.toLowerCase())) {
              if (parentPid == null || line.toLowerCase().contains(parentPid + "")) {
                String[] splitLine = line.trim().split("\\s+");
                int pid = Integer.parseInt(splitLine[splitLine.length - 1]);
                processIds.add(pid);
                LOG.info("Process found: pid={}, commandLine={}", pid, line);
              }
            }
          }
        }
      } catch (Exception e) {
        LOG.error("Couldn't get process list on windows", e);
      }
    } else if (isMac()) {
      try {
        String line;
        String processCmd = "ps aux";
        Process p = Runtime.getRuntime().exec(processCmd);
        LOG.debug("Getting mac process id's for processes command line containing {} with cmd \"{}\"",
            executablePathContains, processCmd);
        try (BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
          while ((line = input.readLine()) != null) {
            LOG.debug("ps aux line: {}", line);
            if (line.contains(executablePathContains)) {
              if (parentPid == null || line.toLowerCase().contains(parentPid + "")) {
                String[] splitLine = line.trim().split("\\s+");
                int pid = Integer.parseInt(splitLine[1]);
                processIds.add(pid);
                LOG.info("Process found: pid={}, commandLine={}", pid, line);
              }
            }
          }
        }
      } catch (Exception e) {
        LOG.error("Couldn't get process list", e);
      }
    } else {
      try {
        String line;
        String processCmd = String.format("ps -eo pid,args");
        LOG.debug("Getting linux process id's where command line contains {} with cmd \"{}\"",
            executablePathContains, processCmd);
        Process p = Runtime.getRuntime().exec(processCmd);
        try (BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
          while ((line = input.readLine()) != null) {
            LOG.debug("ps line: {}", line);
            if (line.contains(executablePathContains)) {
              if (parentPid == null || line.toLowerCase().contains(parentPid + "")) {
                String[] splitLine = line.trim().split("\\s+");
                int pid = Integer.parseInt(splitLine[0]);
                processIds.add(pid);
                LOG.info("Process found: pid={}, commandLine={}", pid, line);
              }
            }
          }
        }
      } catch (Exception e) {
        LOG.error("Couldn't get process list", e);
      }
    }
    return processIds;
  }

  /**
   * Get process id's for a certain executable regardless of operating system.
   *
   * @param executablePathContains The path of the executable you want to get PID's of.
   * @return Set of the PID's.
   */
  public static Set<Integer> getJavaProcessIds(String executablePathContains) {
    return getJavaProcessIds(executablePathContains, null);
  }

  /**
   * Kill the process with the quit signal.
   *
   * @param pid Process ID to kill.
   */
  public static void forceKillPid(int pid) {
    if (isWindows()) {
      try {
        String cmd = String.format(KILL_WINDOWS_TASK, pid);
        LOG.info("Killing process with command: {}", cmd);
        Runtime.getRuntime().exec(cmd);
      } catch (IOException e) {
        LOG.error("Couldn't kill process {}", pid, e);
      }
    } else {
      try {
        String cmd = String.format(KILL_NIX_TASK, pid);
        LOG.info("Killing process with command: {}", cmd);
        Runtime.getRuntime().exec(cmd);
      } catch (IOException e) {
        LOG.error("Couldn't kill process {}", pid, e);
      }
    }
  }

  /**
   * Checks whether the supplied port is available on any local address.
   *
   * @param port the port to check for.
   * @return <code>true</code> if the port is available, otherwise <code>false</code>.
   */
  public static boolean isPortAvailable(int port) {
    ServerSocket socket;
    try {
      socket = new ServerSocket();
    } catch (IOException e) {
      throw new IllegalStateException("Unable to create ServerSocket.", e);
    }

    try {
      InetSocketAddress sa = new InetSocketAddress(port);
      socket.bind(sa);
      return true;
    } catch (IOException ex) {
      return false;
    } finally {
      try {
        socket.close();
      } catch (IOException ex) {
      }
    }
  }

  public static int findAvailablePort(int minPort, int maxPort) {
    int candidatePort = minPort;
    while (!isPortAvailable(candidatePort)) {
      ++candidatePort;
      if (candidatePort > maxPort) {
        throw new IllegalStateException(String.format("Could not find an available port in the range [%d, %d]", minPort, maxPort));
      }
    }
    return candidatePort;
  }
}
