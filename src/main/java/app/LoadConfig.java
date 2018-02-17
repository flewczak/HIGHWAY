package app;

import java.io.File;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.w3c.dom.Element;
import wsd.EmergencyAgent;
import wsd.SignAgent;
import wsd.VehicleAgent;

public class LoadConfig {

    private class AgentParams {

        public final String agent_name_;
        public final String agent_class_name_;
        public final String[] agent_args_;
        public final Double delay_;

        private Double sleep_time_;

        public AgentParams(String agent_name, String agent_class_name, String[] agent_args, Double delay) {
            this.agent_name_ = agent_name;
            this.agent_class_name_ = agent_class_name;
            this.agent_args_ = agent_args;
            this.delay_ = delay;

            this.sleep_time_ = delay;
        }

        Double getSleepTime() {
            return sleep_time_;
        }

        void setSleepTime(Double new_sleep_time) {
            this.sleep_time_ = new_sleep_time;
        }
    }

    private class AgentParamsDelayComparator implements Comparator<AgentParams> {

        public int compare(AgentParams agent_1, AgentParams agent_2) {
            return Double.compare(agent_1.delay_, agent_2.delay_);
        }
    }

    private ArrayList<AgentParams> agent_params_;

    public LoadConfig() {
        agent_params_ = new ArrayList <>();
    }

    //create agents form XML
    public void runAgents(String config_file_name) {

        loadAgents(config_file_name);

        Collections.sort(agent_params_, new AgentParamsDelayComparator());

        for(int i = 1; i < agent_params_.size(); i++)
        {
            agent_params_.get(i).setSleepTime(
                    agent_params_.get(i).getSleepTime() - agent_params_.get(i - 1).delay_
            );
        }

        for(AgentParams element : agent_params_)
        {
            try
            {
                Thread.sleep(Math.round(1000 * element.getSleepTime()));

                CarsApplication.createAgent(element.agent_name_,
                        element.agent_class_name_,
                        element.agent_args_);
            }
            catch (InterruptedException ex)
            {
                Thread.currentThread().interrupt();
            }
        }
    }
    //load parameters of agents from XML
    public void loadAgents(String config_file_name) {

        try {
            File inputFile = new File(config_file_name);
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(inputFile);
            doc.getDocumentElement().normalize();
            loadSigns(doc.getElementsByTagName("sign"));
            loadVehicles(doc.getElementsByTagName("vehicle"));
            loadEmergencyVehicles(doc.getElementsByTagName("emergency"));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //load parameters of SignAgent
    private void loadSigns(NodeList signs) {

        for (int temp = 0; temp < signs.getLength(); temp++) {
            Node sign = signs.item(temp);

            if (sign.getNodeType() == Node.ELEMENT_NODE) {
                Element e_sign = (Element) sign;
                String[] args = {
                        "y_begin:" + e_sign.getElementsByTagName("y_begin").item(0).getTextContent(),
                        "y_end:" + e_sign.getElementsByTagName("y_end").item(0).getTextContent(),
                        "maxSpeed:" + e_sign.getElementsByTagName("max_speed").item(0).getTextContent()
                };

                agent_params_.add(new AgentParams(
                        e_sign.getAttribute("name"), SignAgent.class.getName(), args,
                        0.0));
            }
        }
    }
    //load parameters of VehicleAgent
    private void loadVehicles(NodeList vehicles) {

        for (int temp = 0; temp < vehicles.getLength(); temp++) {
            Node vehicle = vehicles.item(temp);

            if (vehicle.getNodeType() == Node.ELEMENT_NODE) {
                Element e_vehicle = (Element) vehicle;
                String[] args = {
                        "speed:" + e_vehicle.getElementsByTagName("speed").item(0).getTextContent(),
                        "maxSpeed:" + e_vehicle.getElementsByTagName("max_speed").item(0).getTextContent()
                };

                agent_params_.add(new AgentParams(
                        e_vehicle.getAttribute("name"), VehicleAgent.class.getName(), args,
                        Double.parseDouble(e_vehicle.getElementsByTagName("delay").item(0).getTextContent())));
            }
        }
    }
    //load parameters of EmergencyAgent
    private void loadEmergencyVehicles(NodeList emergency_vehicles) {

        for (int temp = 0; temp < emergency_vehicles.getLength(); temp++)
        {
            Node emergency_vehicle = emergency_vehicles.item(temp);

            if (emergency_vehicle.getNodeType() == Node.ELEMENT_NODE)
            {
                Element e_emergency_vehicle = (Element) emergency_vehicle;
                String[] args = {
                        "speed:" + e_emergency_vehicle.getElementsByTagName("speed").item(0).getTextContent(),
                        "maxSpeed:" + e_emergency_vehicle.getElementsByTagName("max_speed").item(0).getTextContent()
                };

                agent_params_.add(new AgentParams(
                        e_emergency_vehicle.getAttribute("name"), EmergencyAgent.class.getName(), args,
                        Double.parseDouble(e_emergency_vehicle.getElementsByTagName("delay").item(0).getTextContent())));
            }
        }
    }
}
