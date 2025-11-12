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

    public List<CardJobsModel> getJobs() {
        return jobs;
    }
}
