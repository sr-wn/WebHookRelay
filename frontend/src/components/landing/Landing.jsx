import Hero from "./Hero.jsx";
import LiveDemoStrip from "./LiveDemoStrip.jsx";
import HowItWorks from "./HowItWorks.jsx";
import FeatureGrid from "./FeatureGrid.jsx";
import CodeSamplePanel from "./CodeSamplePanel.jsx";
import FooterCta from "./FooterCta.jsx";

export default function Landing({ onCreate }) {
  return (
    <div>
      <Hero onCreate={onCreate} />
      <LiveDemoStrip />
      <HowItWorks />
      <FeatureGrid />
      <CodeSamplePanel />
      <FooterCta onCreate={onCreate} />
    </div>
  );
}
