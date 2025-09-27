// Cpyright Aniket 
package com.example.myapplication;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableString;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.speech.tts.TextToSpeech;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    // ---------- Question kinds ----------
    enum QuestionKind {
        TYPE_A_GIVEN_CP_SP_FIND_CATEGORY_AND_AMOUNT, // spinner + amount
        TYPE_B_GIVEN_CP_AND_PL_FIND_SP,              // numeric only
        TYPE_C_GIVEN_SP_AND_PL_FIND_CP               // numeric only
    }

    // Underlying trade for generation
    enum TradeType { PROFIT, LOSS, NONE }

    // ---------- Model ----------
    static class Q {
        final int cp;     // 0..200
        final int sp;     // 0..200
        final QuestionKind kind;

        Q(int cp, int sp, QuestionKind kind) {
            this.cp = cp;
            this.sp = sp;
            this.kind = kind;
        }

        boolean isProfit() { return sp > cp; }
        boolean isLoss()   { return sp < cp; }
        int amount()       { return Math.abs(sp - cp); }

        String categoryText() {
            if (isProfit()) return "PROFIT";
            if (isLoss()) return "LOSS";
            return "NO PROFIT, NO LOSS";
        }

        String prompt() {
            switch (kind) {
                case TYPE_A_GIVEN_CP_SP_FIND_CATEGORY_AND_AMOUNT:
                    return "CP = ₹" + cp + ", SP = ₹" + sp +
                            "\n\nFind PROFIT or LOSS or NO PROFIT, NO LOSS\nHow much is the amount?";
                case TYPE_B_GIVEN_CP_AND_PL_FIND_SP: {
                    String pl = categoryText();
                    return "CP = ₹" + cp + ", " + pl + " = ₹" + amount() +
                            "\n\nWhat is the Selling Price (SP)?";
                }
                case TYPE_C_GIVEN_SP_AND_PL_FIND_CP: {
                    String pl = categoryText();
                    return "SP = ₹" + sp + ", " + pl + " = ₹" + amount() +
                            "\n\nWhat is the Cost Price (CP)?";
                }
            }
            return "";
        }
    }

    // ---------- State ----------
    private final Random rng = new Random();
    private final List<Q> questions = new ArrayList<>();
    private int index = 0;
    private int score = 0;

    // partial credit counter (Type A: category right, amount wrong)
    private int partialCredits = 0;

    // High-score persistence
    private static final String PREFS = "profitloss_prefs";
    private static final String KEY_BEST_SCORE = "best_score";
    private static final String KEY_BEST_PARTIAL = "best_partial";

    // ---------- UI ----------
    private TextView titleTv, progressTv, questionTv, helperTv, answerTv, bestTv;
    private Spinner resultSpinner; // Type A only
    private EditText inputEt;
    private Button submitBtn, nextBtn;

    // ---------- TTS (optional) ----------
    private TextToSpeech tts;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // --- bind views
        titleTv    = findViewById(R.id.titleTv);
        progressTv = findViewById(R.id.progressTv);
        bestTv     = findViewById(R.id.bestTv);
        questionTv = findViewById(R.id.questionTv);
        resultSpinner = findViewById(R.id.resultSpinner);
        inputEt    = findViewById(R.id.inputEt);
        helperTv   = findViewById(R.id.helperTv);
        answerTv   = findViewById(R.id.answerTv);
        submitBtn  = findViewById(R.id.submitBtn);
        nextBtn    = findViewById(R.id.nextBtn);

        // Title styling (bold “Hello” like before)
        SpannableString ss = new SpannableString("Hello Aritra!\n\nProfit & Loss — 10 FUN Questions");
        ss.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                0, 5, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        titleTv.setText(ss);

        // Spinner (with hint)
        String[] options = {"Select Result", "PROFIT", "LOSS", "NO PROFIT, NO LOSS"};
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_item, options) {
            @Override public boolean isEnabled(int position) { return position != 0; }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        resultSpinner.setAdapter(adapter);

        // Input setup
        inputEt.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        inputEt.setFilters(new InputFilter[]{ new InputFilter.LengthFilter(6) });

        // Buttons
        submitBtn.setOnClickListener(v -> onSubmit());
        nextBtn.setOnClickListener(v -> {
            index++;
            showQuestion();
            submitBtn.setVisibility(View.VISIBLE);
            nextBtn.setVisibility(View.GONE);
            answerTv.setVisibility(View.GONE);
        });

        // TTS (optional)
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.ENGLISH);
                tts.setSpeechRate(0.9f);
                tts.setPitch(1.0f);
            }
        });

        regenerateQuestions();
        showBestFromPrefs();
        showQuestion();
    }

    // ---------- Generation with constraints ----------

    private void regenerateQuestions() {
        questions.clear();
        index = 0;
        score = 0;
        partialCredits = 0;
        for (int i = 0; i < 10; i++) {
            questions.add(generateRandomQ());
        }
    }

    private Q generateRandomQ() {
        boolean multiplesOf10 = rng.nextBoolean(); // ~50%
        TradeType trade = randomTrade();
        int cp, sp;

        if (multiplesOf10) {
            cp = (1 + rng.nextInt(20)) * 10; // 10..200
            switch (trade) {
                case NONE:
                    sp = cp;
                    break;
                case PROFIT: {
                    int maxPct = Math.max(1, (int) Math.floor(0.20 * cp));
                    int room = 200 - cp;
                    int maxDelta = Math.min(maxPct, room);
                    int maxDelta10 = (maxDelta / 10) * 10;
                    if (maxDelta10 < 10) maxDelta10 = 10;
                    int steps = Math.max(1, maxDelta10 / 10);
                    int delta = (1 + rng.nextInt(steps)) * 10;
                    sp = cp + delta;
                    break;
                }
                case LOSS: {
                    int maxPct = Math.max(1, (int) Math.floor(0.20 * cp));
                    int room = cp;
                    int maxDelta = Math.min(maxPct, room);
                    int maxDelta10 = (maxDelta / 10) * 10;
                    if (maxDelta10 < 10) maxDelta10 = 10;
                    int steps = Math.max(1, maxDelta10 / 10);
                    int delta = (1 + rng.nextInt(steps)) * 10;
                    sp = cp - delta;
                    break;
                }
                default:
                    sp = cp;
            }
        } else {
            cp = 5 + rng.nextInt(196); // 5..200 (avoid 0 for % math)
            switch (trade) {
                case NONE:
                    sp = cp;
                    break;
                case PROFIT: {
                    int maxPct = Math.max(1, (int) Math.floor(0.20 * cp));
                    int room = Math.max(1, 200 - cp);
                    int maxDelta = Math.max(1, Math.min(maxPct, room));
                    int delta = 1 + rng.nextInt(maxDelta);
                    sp = cp + delta;
                    break;
                }
                case LOSS: {
                    int maxPct = Math.max(1, (int) Math.floor(0.20 * cp));
                    int room = cp;
                    int maxDelta = Math.max(1, Math.min(maxPct, room));
                    int delta = 1 + rng.nextInt(maxDelta);
                    sp = cp - delta;
                    break;
                }
                default:
                    sp = cp;
            }
        }

        cp = clamp(cp, 0, 200);
        sp = clamp(sp, 0, 200);

        // Decide presentation
        QuestionKind kind;
        if (sp == cp) {
            kind = QuestionKind.TYPE_A_GIVEN_CP_SP_FIND_CATEGORY_AND_AMOUNT; // NONE → only A
        } else {
            int r = rng.nextInt(3);
            if (r == 0) kind = QuestionKind.TYPE_A_GIVEN_CP_SP_FIND_CATEGORY_AND_AMOUNT;
            else if (r == 1) kind = QuestionKind.TYPE_B_GIVEN_CP_AND_PL_FIND_SP;
            else kind = QuestionKind.TYPE_C_GIVEN_SP_AND_PL_FIND_CP;
        }

        return new Q(cp, sp, kind);
    }

    private TradeType randomTrade() {
        int t = rng.nextInt(3);
        if (t == 0) return TradeType.PROFIT;
        if (t == 1) return TradeType.LOSS;
        return TradeType.NONE;
    }

    private int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    // ---------- Flow ----------

    private void showBestFromPrefs() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        int best = sp.getInt(KEY_BEST_SCORE, 0);
        int bestPartial = sp.getInt(KEY_BEST_PARTIAL, 0);
        bestTv.setText("Best: " + best + " / 10  (Partial: " + bestPartial + ")");
    }

    private void showQuestion() {
        if (index >= questions.size()) { finishQuiz(); return; }
        Q q = questions.get(index);

        progressTv.setText("Question " + (index + 1) + " of " + questions.size() + "   |   Score: " + score);
        questionTv.setText(q.prompt());

        answerTv.setVisibility(View.GONE);
        nextBtn.setVisibility(View.GONE);
        submitBtn.setVisibility(View.VISIBLE);

        inputEt.setText("");
        inputEt.requestFocus();

        switch (q.kind) {
            case TYPE_A_GIVEN_CP_SP_FIND_CATEGORY_AND_AMOUNT:
                resultSpinner.setVisibility(View.VISIBLE);
                resultSpinner.setSelection(0);
                inputEt.setHint("Enter amount (e.g., 20)");
                helperTv.setText("Tip: Choose category and enter Amount = |SP − CP|.");
                break;

            case TYPE_B_GIVEN_CP_AND_PL_FIND_SP:
                resultSpinner.setVisibility(View.GONE);
                inputEt.setHint("Enter SP (e.g., 150)");
                helperTv.setText("Tip: SP = CP ± Profit/Loss amount.");
                break;

            case TYPE_C_GIVEN_SP_AND_PL_FIND_CP:
                resultSpinner.setVisibility(View.GONE);
                inputEt.setHint("Enter CP (e.g., 120)");
                helperTv.setText("Tip: CP = SP ∓ Profit/Loss amount.");
                break;
        }
    }

    private void onSubmit() {
        if (index >= questions.size()) { finishQuiz(); return; }
        Q q = questions.get(index);

        String userText = inputEt.getText().toString().trim();
        if (userText.isEmpty()) {
            inputEt.setError("Please enter your answer");
            return;
        }

        Integer userNum = null;
        try { userNum = Integer.parseInt(userText); } catch (Exception ignored) {}
        if (userNum == null) {
            inputEt.setError("Please enter a valid number");
            return;
        }

        boolean correct;
        String expectedText;

        switch (q.kind) {
            case TYPE_A_GIVEN_CP_SP_FIND_CATEGORY_AND_AMOUNT: {
                String selected = resultSpinner.getSelectedItem().toString();
                boolean categoryCorrect =
                        (selected.equals("PROFIT") && q.isProfit()) ||
                                (selected.equals("LOSS") && q.isLoss()) ||
                                (selected.equals("NO PROFIT, NO LOSS") && !q.isProfit() && !q.isLoss());
                boolean amountCorrect = (userNum == q.amount());

                if (!categoryCorrect && amountCorrect) {
                    // user guessed amount but wrong category (rare on NONE)
                    partialCredits++; // still count as partial effort
                } else if (categoryCorrect && !amountCorrect) {
                    partialCredits++; // category right = partial credit
                }

                correct = categoryCorrect && amountCorrect;
                expectedText = q.categoryText() + " = ₹" + q.amount();
                break;
            }

            case TYPE_B_GIVEN_CP_AND_PL_FIND_SP: {
                correct = (userNum == q.sp);
                expectedText = "SP = ₹" + q.sp + "  (" + q.categoryText() + " = ₹" + q.amount() + ")";
                break;
            }

            case TYPE_C_GIVEN_SP_AND_PL_FIND_CP: {
                correct = (userNum == q.cp);
                expectedText = "CP = ₹" + q.cp + "  (" + q.categoryText() + " = ₹" + q.amount() + ")";
                break;
            }

            default:
                correct = false;
                expectedText = "";
        }

        if (correct) {
            score++;
            playSound(R.raw.correct);

            // Speak after 500ms and auto-advance after 500ms
            submitBtn.postDelayed(() -> speak("Good Job Aritra"), 500);
            submitBtn.postDelayed(() -> {
                index++;
                showQuestion();
            }, 500);

            Toast.makeText(this, "✅ Correct!", Toast.LENGTH_SHORT).show();
        } else {
            playSound(R.raw.wrong);
            Toast.makeText(this, "❌ Try again!", Toast.LENGTH_SHORT).show();

            // Reveal correct answer and show NEXT; hide Submit
            answerTv.setText("Correct: " + expectedText);
            answerTv.setVisibility(View.VISIBLE);
            submitBtn.setVisibility(View.GONE);
            nextBtn.setVisibility(View.VISIBLE);
        }
    }

    private void finishQuiz() {
        submitBtn.setEnabled(false);
        inputEt.setEnabled(false);
        resultSpinner.setEnabled(false);
        nextBtn.setEnabled(false);

        String message = "Quiz Finished!\nYour Score: " + score + " / " + questions.size() +
                "\nPartial credits (Type A): " + partialCredits;

        if (score == 10) {
            message += "\n🎉 Excellent!";
            playSound(R.raw.outstanding);
            speak("Well Done Aritra. Keep it up");
        } else if (score >= 8) {
            message += "\n👍 Good.";
            playSound(R.raw.outstanding);
            speak("Well Done Aritra");
        }

        questionTv.setText(message);

        // Save bests
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        int best = sp.getInt(KEY_BEST_SCORE, 0);
        int bestPartial = sp.getInt(KEY_BEST_PARTIAL, 0);
        if (score > best || (score == best && partialCredits > bestPartial)) {
            sp.edit()
                    .putInt(KEY_BEST_SCORE, score)
                    .putInt(KEY_BEST_PARTIAL, partialCredits)
                    .apply();
        }
        showBestFromPrefs();

        // Add Restart button dynamically (below NEXT area)
        Button restartBtn = findViewById(R.id.restartBtn);
        if (restartBtn != null) {
            restartBtn.setVisibility(View.VISIBLE);
            restartBtn.setOnClickListener(v -> {
                // reset
                regenerateQuestions();
                inputEt.setEnabled(true);
                resultSpinner.setEnabled(true);
                submitBtn.setEnabled(true);
                nextBtn.setEnabled(true);
                submitBtn.setText("Submit");
                showQuestion();
                v.setVisibility(View.GONE); // hide restart
            });
        }
    }

    // ---------- Sound / TTS helpers ----------

    private void playSound(int soundResId) {
        MediaPlayer mp = MediaPlayer.create(this, soundResId);
        if (mp != null) {
            mp.setOnCompletionListener(MediaPlayer::release);
            mp.start();
        }
    }

    private void speak(String text) {
        if (tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utteranceId");
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            try { tts.stop(); tts.shutdown(); } catch (Exception ignored) {}
        }
        super.onDestroy();
    }
}
