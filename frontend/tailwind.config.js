/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ["./src/**/*.{html,ts}"],
  theme: {
    extend: {
      colors: {
        agentops: {
          bg: "#0b0f17",
          card: "#121826",
          accent: "#5b8def"
        }
      }
    }
  },
  plugins: []
};
