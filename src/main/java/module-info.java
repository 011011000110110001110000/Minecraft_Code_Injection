module SusInjection.main {
    requires jdk.unsupported;
    requires java.rmi;
    requires jdk.attach;
    requires org.jetbrains.annotations;
    requires java.instrument;

    exports me.lollopollqo.sus.injection.util;
    exports me.lollopollqo.sus.injection.rmi;
    exports me.lollopollqo.sus.injection.agent;
}