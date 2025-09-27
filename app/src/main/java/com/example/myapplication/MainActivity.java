package com.example.myapplication;

import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableString;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import android.speech.tts.TextToSpeech;
import java.util.Locale;


public class MainActivity extends AppCompatActivity {

    // ----- Model -----
    static class Q {
        final int cp;
        final int sp;
        Q(int cp, int sp) { this.cp = cp; this.sp = sp; }
        boolean isProfit() { return sp > cp; }
        boolean isLoss()   { return sp < cp; }
        int amount()       { return Math.abs(sp - cp); }
        String text() {
            return "CP = ₹" + cp + ", SP = ₹" + sp
                    + "\n\nFind PROFIT or LOSS or NO PROFIT, NO LOSS\nHow much is the amount?";
        }
        String categoryText() {
            if (isProfit()) return "PROFIT";
            if (isLoss()) return "LOSS";
            return "NO PROFIT, NO LOSS";
        }
    }

    private final List<Q> questions = new ArrayList<>();
    private int index = 0;
    private int score = 0;
    private final Random rng = new Random();

    // ----- UI -----
    private TextView titleTv, progressTv, questionTv, helperTv, answerTv;
    private EditText inputEt;
    private Button submitBtn, nextBtn;
    private Spinner resultSpinner;

    private TextToSpeech tts;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        regenerateQuestions(); // fresh random set each launch / restart

        // Root layout
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        root.setGravity(Gravity.TOP);

        // Title
        titleTv = new TextView(this);
        String text = "Hello Aritra!\n\nProfit & Loss — 10 FUN Questions";
        SpannableString ss = new SpannableString(text);
        ss.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                0, 4, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        titleTv.setText(ss);
        titleTv.setTextSize(22f);
        titleTv.setPadding(0, dp(24), 0, dp(12));
        root.addView(titleTv);

        // Progress
        progressTv = new TextView(this);
        progressTv.setTextSize(16f);
        progressTv.setPadding(0, 0, 0, dp(8));
        root.addView(progressTv);

        // Question
        questionTv = new TextView(this);
        questionTv.setTextSize(20f);
        questionTv.setPadding(0, 0, 0, dp(16));
        root.addView(questionTv);

        // Spinner (with hint)
        resultSpinner = new Spinner(this);
        resultSpinner.setBackgroundResource(R.drawable.spinner_border);
        String[] options = {"Select Result", "PROFIT", "LOSS", "NO PROFIT, NO LOSS"};
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_item, options) {
            @Override public boolean isEnabled(int position) { return position != 0; }
            @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                TextView tv = (TextView) view;
                tv.setTextColor(position == 0 ? Color.RED : Color.GREEN);
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        resultSpinner.setAdapter(adapter);
        LinearLayout.LayoutParams spLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        spLp.setMargins(0, 0, 0, dp(12));
        resultSpinner.setLayoutParams(spLp);
        root.addView(resultSpinner);

        // Amount input
        inputEt = new EditText(this);
        inputEt.setHint("Enter amount (e.g., 15)");
        inputEt.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        inputEt.setFilters(new InputFilter[]{ new InputFilter.LengthFilter(6) });
        LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        etLp.setMargins(0, 0, 0, dp(12));
        inputEt.setLayoutParams(etLp);
        root.addView(inputEt);

        // Helper text
        helperTv = new TextView(this);
        helperTv.setText("Tip: Choose category and enter Amount = |SP − CP|.");
        helperTv.setTextSize(14f);
        helperTv.setPadding(0, 0, 0, dp(8));
        root.addView(helperTv);

        // Correct answer label (hidden until needed)
        answerTv = new TextView(this);
        answerTv.setTextSize(16f);
        answerTv.setTextColor(Color.parseColor("#B00020")); // material error red-ish
        answerTv.setVisibility(View.GONE);
        answerTv.setPadding(0, 0, 0, dp(8));
        root.addView(answerTv);

        // Submit button
        submitBtn = new Button(this);
        submitBtn.setText("Submit");
        submitBtn.setOnClickListener(v -> onSubmit());
        root.addView(submitBtn);

        // NEXT button (hidden until a wrong answer)
        nextBtn = new Button(this);
        nextBtn.setText("NEXT");
        nextBtn.setVisibility(View.GONE);
        nextBtn.setOnClickListener(v -> {
            // Move to next question and clean up UI
            index++;
            showQuestion();        // resets fields
            submitBtn.setVisibility(View.VISIBLE);
            nextBtn.setVisibility(View.GONE);
            answerTv.setVisibility(View.GONE);
        });
        LinearLayout.LayoutParams nbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nbLp.topMargin = dp(8);
        nextBtn.setLayoutParams(nbLp);
        root.addView(nextBtn);

        setContentView(root);
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(Locale.ENGLISH);
                tts.setSpeechRate(0.9f);  // slower speech
                tts.setPitch(1.0f);       // normal pitch
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                        result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Toast.makeText(this, "TTS language not supported!", Toast.LENGTH_SHORT).show();
                }
            }
        });

        showQuestion();
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
    private void speak(String text) {
        if (tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utteranceId");
        }
    }

    // -------------------- RANDOM QUESTION GENERATION --------------------

    private void regenerateQuestions() {
        questions.clear();
        index = 0;
        score = 0;
        for (int i = 0; i < 10; i++) questions.add(generateRandomQ());
    }

    /** |SP − CP| ≤ 20% of CP ; CP, SP ∈ [0..200]; type random */
    /** Create one random question with |SP − CP| ≤ 20% of CP (and CP, SP within 0..200).
     * 50% of the time, CP and SP are multiples of 10.
     */
    private Q generateRandomQ() {
        int type = rng.nextInt(3); // 0=PROFIT, 1=LOSS, 2=NONE
        boolean useMultiplesOf10 = rng.nextBoolean(); // 50% true

        int cp;
        int sp;

        if (useMultiplesOf10) {
            // CP as multiple of 10, avoid 0 to keep 20% > 0
            cp = (1 + rng.nextInt(20)) * 10; // 10..200
        } else {
            cp = 5 + rng.nextInt(196); // 5..200
        }

        if (type == 2) {
            sp = cp;
        } else if (type == 0) { // PROFIT
            int maxPct = Math.max(1, (int)Math.floor(0.20 * cp));
            int maxRoom = Math.max(1, 200 - cp);
            int maxDelta = Math.max(1, Math.min(maxPct, maxRoom));
            int delta = 1 + rng.nextInt(maxDelta);

            sp = cp + delta;
            if (useMultiplesOf10) {
                // Round SP to nearest multiple of 10 within limit
                sp = Math.min(200, Math.max(0, Math.round(sp / 10f) * 10));
                if (sp == cp) sp = cp + 10; // ensure difference if possible
            }
        } else { // LOSS
            int maxPct = Math.max(1, (int)Math.floor(0.20 * cp));
            int maxRoom = cp;
            int maxDelta = Math.max(1, Math.min(maxPct, maxRoom));
            int delta = 1 + rng.nextInt(maxDelta);

            sp = cp - delta;
            if (useMultiplesOf10) {
                // Round SP to nearest multiple of 10 within limit
                sp = Math.min(200, Math.max(0, Math.round(sp / 10f) * 10));
                if (sp == cp) sp = Math.max(0, cp - 10); // ensure difference if possible
            }
        }

        cp = clamp(cp, 0, 200);
        sp = clamp(sp, 0, 200);
        return new Q(cp, sp);
    }

    private int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    // -------------------- QUIZ FLOW --------------------

    private void showQuestion() {
        if (index >= questions.size()) { finishQuiz(); return; }
        Q q = questions.get(index);
        progressTv.setText("Question " + (index + 1) + " of " + questions.size() + "   |   Score: " + score);
        questionTv.setText(q.text());
        inputEt.setText("");
        resultSpinner.setSelection(0); // "Select Result"
        inputEt.requestFocus();

        // Ensure the per-question widgets are in the clean state
        submitBtn.setVisibility(View.VISIBLE);
        nextBtn.setVisibility(View.GONE);
        answerTv.setVisibility(View.GONE);
    }

    private void playSound(int soundResId) {
        MediaPlayer mp = MediaPlayer.create(this, soundResId);
        if (mp != null) {
            mp.setOnCompletionListener(MediaPlayer::release);
            mp.start();
        }
    }

    private void onSubmit() {
        if (index >= questions.size()) { finishQuiz(); return; }

        String userText = inputEt.getText().toString().trim();
        if (userText.isEmpty()) { inputEt.setError("Please enter an amount"); return; }

        Integer userAns = null;
        try { userAns = Integer.parseInt(userText); } catch (NumberFormatException ignored) {}
        if (userAns == null) { inputEt.setError("Please enter a valid number"); return; }

        Q q = questions.get(index);

        // Check category
        String selected = resultSpinner.getSelectedItem().toString();
        boolean categoryCorrect =
                (selected.equals("PROFIT") && q.isProfit()) ||
                        (selected.equals("LOSS") && q.isLoss()) ||
                        (selected.equals("NO PROFIT, NO LOSS") && !q.isProfit() && !q.isLoss());

        // Check amount
        boolean amountCorrect = (userAns == q.amount());
        boolean correct = categoryCorrect && amountCorrect;

        if (correct) {
            score++;
            playSound(R.raw.correct);

            // Brief toast + auto-advance after 500ms
            Toast.makeText(this,
                    "✅ Correct! " + q.categoryText() + " = ₹" + q.amount(),
                    Toast.LENGTH_SHORT).show();

            // Delay TTS by 500ms
            submitBtn.postDelayed(() -> {
                speak("Good Job Aritra");
            }, 500);

            // auto move after 500 ms more (e.g., 1000ms total)
            submitBtn.postDelayed(() -> {
                index++;
                showQuestion();
            }, 1000);

        } else {
            // Wrong: play angry, do NOT advance
            playSound(R.raw.wrong);

            // Feedback toast
            String msg;
            if (!categoryCorrect && !amountCorrect) {
                msg = "❌ Wrong category and amount.";
            } else if (!categoryCorrect) {
                msg = "❌ Wrong category.";
            } else {
                msg = "❌ Wrong amount.";
            }
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();

            // Hide Submit, show correct answer + NEXT
            submitBtn.setVisibility(View.GONE);
            answerTv.setText("Correct: " + q.categoryText() + " = ₹" + q.amount());
            answerTv.setVisibility(View.VISIBLE);
            nextBtn.setVisibility(View.VISIBLE);
        }
    }

    private void finishQuiz() {
        submitBtn.setEnabled(false);
        inputEt.setEnabled(false);
        resultSpinner.setEnabled(false);
        nextBtn.setEnabled(false);

        String message = "Quiz Finished!\nYour Score: " + score + " / " + questions.size();
        if (score == 10) {
            message += "\n🎉 Excellent!";
            speak("Excellent. Well Done Aritra. Keep it up");

            //playSound(R.raw.outstanding);
        } else if (score >= 7 ){
            message += "\n👍 Good.";
            speak("Well Done Aritra. Keep it up");

            //playSound(R.raw.outstanding);
        }

        questionTv.setText(message);
        progressTv.setText("Great job!");

        Button restartBtn = new Button(this);
        restartBtn.setText("Restart");
        restartBtn.setOnClickListener(v -> {
            regenerateQuestions();  // fresh random questions on restart
            inputEt.setEnabled(true);
            resultSpinner.setEnabled(true);
            submitBtn.setEnabled(true);
            nextBtn.setEnabled(true);
            submitBtn.setText("Submit");
            showQuestion();
        });

        LinearLayout parent = (LinearLayout) submitBtn.getParent();
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        parent.addView(restartBtn, lp);
    }

    private int dp(int px) { return (int) (px * getResources().getDisplayMetrics().density); }
}
