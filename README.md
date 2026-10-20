# Emergency Room Patient Manager

A beginner-friendly Java Spring Boot application with a responsive HTML/CSS/JavaScript frontend.

## Requirements

- JDK 17 or newer
- Maven 3.6 or newer

## Run the application

1. Extract the `ERPatientManager.zip` file.
2. Open a terminal in the extracted `ERPatientManager` folder (the folder containing `pom.xml`).
3. Run:

   ```bash
   mvn spring-boot:run
   ```

4. Wait until Spring Boot reports that the application has started.
5. Open <http://localhost:8080/> in your browser.

Keep the terminal open while using the app.

## Features

- Register a patient with severity 1–5.
- Higher severity is treated first (5 is highest).
- Patients with equal severity are served in order of arrival.
- View the waiting queue and treatment history.
- Undo the latest treatment and return that patient to the waiting queue.
- Responsive layout for desktop and mobile screens.

## API endpoints

- `GET /api/state` — return waiting list and treatment history.
- `POST /api/patient` — register a patient; JSON body example: `{"name":"Asha","severity":5}`.
- `POST /api/treat` — treat the next patient.
- `POST /api/undo` — undo the most recent treatment.

## Notes

The application stores data in memory. Restarting the server clears all patients and treatment history. This is an academic simulation, not a clinically validated triage system.
