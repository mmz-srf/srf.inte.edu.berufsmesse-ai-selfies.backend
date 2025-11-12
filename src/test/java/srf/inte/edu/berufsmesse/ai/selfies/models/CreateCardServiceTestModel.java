package srf.inte.edu.berufsmesse.ai.selfies.models;

import srf.inte.edu.berufsmesse.ai.selfies.model.cards.CardJobsModel;

import java.util.List;

public class CreateCardServiceTestModel {
    private final List<CardJobsModel> jobs = List.of(
      new CardJobsModel("Veranstaltungsfachmann/-fachfrau", 25),
      new CardJobsModel("Informatiker:in Plattform", 28),
      new CardJobsModel("Informatiker:in Applikation", 26),
      new CardJobsModel("Multimediaelektroniker:in", 25),
      new CardJobsModel("ICT-Fachmann/Fachfrau", 28),
      new CardJobsModel("Fachperson Information und Dokumentation", 24),
      new CardJobsModel("Automobilmechatroniker:in", 25),
      new CardJobsModel("Mediamatiker:in", 28),
      new CardJobsModel("Praktikum HMS Kaufmann/Kauffrau", 28)
    );

    private final List<String> ickList = List.of(
            "Stimmungsbombe",
            "Macher:in",
            "Alpha fe:male",
            "Comedian",
            "Professioneller Aufschieber:in",
            "Experte im so tun, als sei man beschäftigt.",
            "Gaslighting Pro",
            "Experte im sich alles merken.",
            "Übermenschliche Stärke",
            "Experte im alles verlieren.",
            "Experte im sich vor Aufgaben drücken.",
            "Mit Tieren reden zu können.",
            "Übermenschliche Geschwindigkeit",
            "Meister:in der passiv-aggressiven Kommunikation.",
            "Experte im Argumente finden die logisch klingen, es aber nicht sind.",
            "Experte im unsichtbar werden, wenn Hilfe benötigt wird.",
            "Experte im immer Müde sein, egal wie viel man geschlafen hat.",
            "Ultrareflexe",
            "Serien-binge-Watcher",
            "Profi im nie Erreichbar sein",
            "Meister:in in der perfekten Ausrede finden.",
            "Chaosmagnet",
            "Experte in das Drama von jedem zu wissen."
    );

    public List<CardJobsModel> getJobs() {
        return jobs;
    }

    public List<String> getIckList() {
        return ickList;
    }
}
