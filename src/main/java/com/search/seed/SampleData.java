package com.search.seed;

import com.search.model.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * A small built-in corpus across several categories so the demo works out of the box
 * and search/trending have interesting data. The benchmark uses a generated superset.
 */
public final class SampleData {

    private SampleData() {}

    public static List<Document> create() {
        List<Document> docs = new ArrayList<>();

        // programming
        docs.add(new Document("Introduction to Java", "Java is a class-based object oriented programming language. Java streams let you process collections declaratively.", "programming"));
        docs.add(new Document("Java Streams Tutorial", "Java streams provide a declarative approach to process sequences of elements. Streams support map filter and reduce operations.", "programming"));
        docs.add(new Document("JavaScript Basics", "JavaScript is the programming language of the web. JavaScript runs in the browser and on servers with node.", "programming"));
        docs.add(new Document("Python for Data Science", "Python is widely used for data science and machine learning. Python libraries like pandas and numpy are powerful.", "programming"));
        docs.add(new Document("Understanding Concurrency in Java", "Java concurrency uses threads and executors. Locks and concurrent collections make multithreading safer.", "programming"));
        docs.add(new Document("Data Structures and Algorithms", "A solid grasp of data structures and algorithms is essential. Trees graphs heaps and hash maps appear everywhere.", "programming"));
        docs.add(new Document("Spring Boot Quickstart", "Spring Boot makes it easy to create stand alone production grade applications. Spring Boot auto configures the web layer.", "programming"));
        docs.add(new Document("SQL for Beginners", "SQL lets you query relational databases. Learn select where group by and joins to analyze data effectively.", "programming"));

        // cooking
        docs.add(new Document("How to Make Pasta", "Boil water add salt cook pasta until al dente. Toss with olive oil garlic and parmesan cheese.", "cooking"));
        docs.add(new Document("Best Chocolate Cake Recipe", "A rich chocolate cake needs cocoa flour sugar eggs and butter. Bake until a toothpick comes out clean.", "cooking"));
        docs.add(new Document("Indian Curry Guide", "Indian curry uses turmeric cumin coriander and garam masala. Serve curry with rice or naan bread.", "cooking"));
        docs.add(new Document("Fresh Salad Ideas", "A fresh salad combines greens tomatoes cucumber and a light vinaigrette. Salads are healthy and quick.", "cooking"));
        docs.add(new Document("Sourdough Bread at Home", "Sourdough bread relies on a wild yeast starter. Feed the starter daily and bake with flour water and salt.", "cooking"));

        // travel
        docs.add(new Document("Visiting Paris", "Paris is famous for the Eiffel Tower the Louvre and croissants. Walk along the Seine and visit Montmartre.", "travel"));
        docs.add(new Document("Tokyo Travel Guide", "Tokyo mixes old temples and new skyscrapers. Try sushi at Tsukiji and explore Shibuya crossing at night.", "travel"));
        docs.add(new Document("Backpacking in Nepal", "Nepal offers trekking in the Himalayas. The Everest base camp trek is popular and challenging.", "travel"));
        docs.add(new Document("Road Trip Across Iceland", "Iceland has waterfalls geysers and glaciers. A road trip on the ring road shows the best of Iceland.", "travel"));
        docs.add(new Document("Beaches of Bali", "Bali beaches are great for surfing and sunsets. Visit Ubud for rice terraces and temples.", "travel"));

        // health
        docs.add(new Document("Benefits of Running", "Running improves cardiovascular health and boosts mood. Start slow and build distance gradually to avoid injury.", "health"));
        docs.add(new Document("Understanding Sleep", "Good sleep is essential for health. Aim for seven to nine hours and keep a consistent schedule.", "health"));
        docs.add(new Document("Healthy Eating Habits", "Eat more vegetables fruits and whole grains. Limit sugar and processed foods for better long term health.", "health"));
        docs.add(new Document("Yoga for Beginners", "Yoga improves flexibility and reduces stress. Start with basic poses and focus on your breath.", "health"));
        docs.add(new Document("Hydration and Health", "Drinking enough water supports digestion and energy. Carry a water bottle and sip through the day.", "health"));

        // finance
        docs.add(new Document("Basics of Investing", "Investing in stocks and bonds builds wealth over time. Diversify your portfolio to manage risk.", "finance"));
        docs.add(new Document("How Budgeting Works", "A budget tracks income and expenses. Use the fifty thirty twenty rule for needs wants and savings.", "finance"));
        docs.add(new Document("Understanding Credit Scores", "A credit score affects loans and interest rates. Pay bills on time and keep credit utilization low.", "finance"));
        docs.add(new Document("Index Funds Explained", "Index funds track a market index and have low fees. They are a simple way to invest for the long term.", "finance"));
        docs.add(new Document("Saving for Retirement", "Start saving for retirement early. Compound interest makes early contributions far more valuable.", "finance"));

        // sports
        docs.add(new Document("Rules of Cricket", "Cricket is played with a bat and ball between two teams. Test matches last up to five days.", "sports"));
        docs.add(new Document("Soccer World Cup History", "The soccer world cup is held every four years. Brazil has won the world cup five times.", "sports"));
        docs.add(new Document("Marathon Training Plan", "Train for a marathon with long runs tempo runs and rest days. Build weekly mileage gradually.", "sports"));
        docs.add(new Document("Tennis Grand Slams", "Tennis has four grand slams: Wimbledon the US Open the French Open and the Australian Open.", "sports"));
        docs.add(new Document("Basketball Basics", "Basketball teams score by shooting the ball through a hoop. NBA games have four twelve minute quarters.", "sports"));

        return docs;
    }
}
