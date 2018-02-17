package wsd;

import graphics.GUIApp;
import jade.content.lang.Codec;
import jade.content.lang.sl.SLCodec;
import jade.content.onto.Ontology;
import jade.content.onto.OntologyException;
import jade.content.onto.basic.Action;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.domain.FIPANames;
import jade.lang.acl.ACLMessage;
import ontology.ParametersOntology;
import ontology.SignParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;


public class SignAgent extends Agent {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    SignParameters myParameters;
    Ontology ontology = ParametersOntology.getInstance();

    private static final String AGENT_TYPE = "sign_agent";
    public static final String CAR_AGENT_TYPE = "vehicle_agent";

    // setup of SignAgent with parameters
    @Override
    protected void setup() {

        Object[] args = getArguments();

        if (args.length != 3)
            throw new IllegalStateException("Needs more arguments");

        Long y_begin = Long.parseLong(args[0].toString().split(":")[1]);
        Long y_end = Long.parseLong(args[1].toString().split(":")[1]);
        Long limit_max_speed = Long.parseLong(args[2].toString().split(":")[1]);

        System.out.println("Utworzono Agenta typu ZNAK: " + getName() + ", y_begin: " + y_begin + ", y_end: " + y_end + ", Limit_MaxSpeed: " + limit_max_speed);
        getContentManager().registerLanguage(new SLCodec(), FIPANames.ContentLanguage.FIPA_SL0);
        getContentManager().registerOntology(ontology);

        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType(AGENT_TYPE);
        sd.setName(getName());
        dfd.addServices(sd);

        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        myParameters = new SignParameters(y_begin, y_end, limit_max_speed);
        addBehaviour(new SendParametersSign(this,100));

        GUIApp.onSetupSign(getAID(), myParameters.getYBegin(), myParameters.getYEnd(), myParameters.getLimitMaxSpeed());

    }

    //cyclic sending parameters of SignAgent
    class SendParametersSign extends TickerBehaviour {
        public SendParametersSign(Agent a, int period) {
            super(a, period);
        }

        @Override
        protected void onTick() {
            SendParameters();
        }
    }
    private void SendParameters() {
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        List<AID> receivers = CarsMap.getAllAgentsOfOneType(this, CAR_AGENT_TYPE);
        AID receiver = new AID("AgentCar", AID.ISLOCALNAME);

        for (AID rec : receivers) {
            msg.addReceiver(rec);
        }
        msg.setLanguage(FIPANames.ContentLanguage.FIPA_SL0);
        msg.setOntology(ParametersOntology.NAME);
        try {
            getContentManager().fillContent(msg, new Action(receiver, myParameters));
        } catch (Codec.CodecException | OntologyException e) {
            e.printStackTrace();
        }
        send(msg);
    }

}